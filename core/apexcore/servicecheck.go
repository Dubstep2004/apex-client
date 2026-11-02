package apexcore

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"

	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

// Проверка сервисов по серверу (3.5): открываются ли ChatGPT, YouTube Premium, Netflix и Gemini
// через конкретный сервер. Запросы идут через локальный SOCKS ядра «probe-in» с пользователем
// n:<сервер> — тот же путь, что у замеров DNS, VPN для этого переключать не нужно.

// Итог по сервису: yes — работает, no — недоступен в стране сервера,
// partial — частично (Netflix: только свои сериалы), error — не ответил.
type serviceResult struct {
	Status string `json:"status"`
	Region string `json:"region,omitempty"`
}

type serviceReport struct {
	ChatGPT  serviceResult `json:"chatgpt"`
	YouTube  serviceResult `json:"youtube"`
	Netflix  serviceResult `json:"netflix"`
	Gemini   serviceResult `json:"gemini"`
	Country  string        `json:"country,omitempty"`
	Duration int64         `json:"ms"`
}

const browserUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

// Адреса проверок — переменные, чтобы тесты подменяли их локальным сервером.
var (
	urlTrace    = "https://chatgpt.com/cdn-cgi/trace"
	urlOpenAI   = "https://api.openai.com/compliance/cookie_requirements"
	urlYouTube  = "https://www.youtube.com/premium"
	urlNetflix1 = "https://www.netflix.com/title/81280792" // сериал Netflix (есть почти везде, где работает Netflix)
	urlNetflix2 = "https://www.netflix.com/title/70143836" // лицензионный фильм (есть не везде)
	urlGemini   = "https://gemini.google.com/"
)

// ServiceCheck проверяет сервисы через сервер tag (ядро должно работать). Ответ — JSON serviceReport.
func ServiceCheck(tag string, timeoutMs int32) string {
	if timeoutMs <= 0 {
		timeoutMs = 8000
	}
	pass := probePass()
	if pass == "" || probeAddr().Port == 0 {
		return `{"error":"VPN выключен"}`
	}
	client := socks.NewClient(N.SystemDialer, probeAddr(), socks.Version5, "n:"+tag, pass)
	dial := func(ctx context.Context, network, addr string) (net.Conn, error) {
		return client.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddr(addr))
	}
	return runServiceCheck(dial, time.Duration(timeoutMs)*time.Millisecond)
}

func runServiceCheck(dial func(ctx context.Context, network, addr string) (net.Conn, error), timeout time.Duration) string {
	start := time.Now()
	hc := &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			DialContext:         dial,
			TLSHandshakeTimeout: timeout,
			ForceAttemptHTTP2:   true,
			DisableKeepAlives:   true,
		},
		// Netflix и Google отвечают переадресацией — её код и адрес нам и нужны
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	get := func(url string) (int, string, string, error) {
		req, _ := http.NewRequest(http.MethodGet, url, nil)
		req.Header.Set("User-Agent", browserUA)
		req.Header.Set("Accept-Language", "en-US,en;q=0.9")
		resp, err := hc.Do(req)
		if err != nil {
			return 0, "", "", err
		}
		defer resp.Body.Close()
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
		return resp.StatusCode, string(b), resp.Header.Get("Location"), nil
	}

	var r serviceReport
	var wg sync.WaitGroup
	wg.Add(5)
	go func() {
		defer wg.Done()
		if _, body, _, err := get(urlTrace); err == nil {
			r.Country = traceCountry(body)
		}
	}()
	go func() {
		defer wg.Done()
		code, body, _, err := get(urlOpenAI)
		r.ChatGPT = chatgptResult(code, body, err)
	}()
	go func() {
		defer wg.Done()
		code, body, _, err := get(urlYouTube)
		r.YouTube = youtubeResult(code, body, err)
	}()
	go func() {
		defer wg.Done()
		c1, _, _, e1 := get(urlNetflix1)
		c2, _, loc, e2 := get(urlNetflix2)
		r.Netflix = netflixResult(c1, e1, c2, e2, loc)
	}()
	go func() {
		defer wg.Done()
		code, body, _, err := get(urlGemini)
		r.Gemini = geminiResult(code, body, err)
	}()
	wg.Wait()
	if r.ChatGPT.Status == "yes" && r.ChatGPT.Region == "" {
		r.ChatGPT.Region = r.Country
	}
	r.Duration = time.Since(start).Milliseconds()
	out, _ := json.Marshal(r)
	return string(out)
}

func traceCountry(body string) string {
	for _, line := range strings.Split(body, "\n") {
		if strings.HasPrefix(line, "loc=") {
			return strings.ToUpper(strings.TrimSpace(strings.TrimPrefix(line, "loc=")))
		}
	}
	return ""
}

// ChatGPT: в неподдерживаемой стране API отвечает «unsupported_country».
func chatgptResult(code int, body string, err error) serviceResult {
	switch {
	case err != nil || code == 0:
		return serviceResult{Status: "error"}
	case strings.Contains(body, "unsupported_country"):
		return serviceResult{Status: "no"}
	case code >= 500:
		return serviceResult{Status: "error"}
	default:
		return serviceResult{Status: "yes"}
	}
}

var ytRegion = regexp.MustCompile(`"(?:INNERTUBE_CONTEXT_GL|countryCode)"\s*:\s*"([A-Z]{2})"`)

// YouTube Premium: страница /premium в неподдерживаемой стране пишет, что Premium недоступен.
func youtubeResult(code int, body string, err error) serviceResult {
	if err != nil || code == 0 || code >= 500 {
		return serviceResult{Status: "error"}
	}
	region := ""
	if m := ytRegion.FindStringSubmatch(body); m != nil {
		region = m[1]
	}
	low := strings.ToLower(body)
	if strings.Contains(low, "premium is not available in your country") || strings.Contains(low, "not available in your country") || region == "RU" || region == "CN" {
		return serviceResult{Status: "no", Region: region}
	}
	if code == 200 {
		return serviceResult{Status: "yes", Region: region}
	}
	return serviceResult{Status: "error", Region: region}
}

var nfRegion = regexp.MustCompile(`netflix\.com/([a-z]{2})(?:-[a-z]{2})?/title`)

// Netflix: оба фильма открываются — полный каталог; только сериал Netflix — «только свои»; 404/403 у обоих — нет.
func netflixResult(c1 int, e1 error, c2 int, e2 error, location string) serviceResult {
	if e1 != nil && e2 != nil {
		return serviceResult{Status: "error"}
	}
	region := ""
	if m := nfRegion.FindStringSubmatch(location); m != nil {
		region = strings.ToUpper(m[1])
	}
	ok := func(c int) bool { return c == 200 || c == 301 || c == 302 }
	switch {
	case ok(c2):
		return serviceResult{Status: "yes", Region: region}
	case ok(c1):
		return serviceResult{Status: "partial", Region: region}
	case c1 == 403 || c1 == 404 || c2 == 403 || c2 == 404:
		return serviceResult{Status: "no"}
	default:
		return serviceResult{Status: "error"}
	}
}

// Gemini: в поддерживаемой стране страница содержит флаг доступности 45631641,null,true;
// в неподдерживаемой — переадресация на страницу «недоступно» или без флага.
func geminiResult(code int, body string, err error) serviceResult {
	switch {
	case err != nil || code == 0 || code >= 500:
		return serviceResult{Status: "error"}
	case strings.Contains(body, "45631641,null,true"):
		return serviceResult{Status: "yes"}
	default:
		return serviceResult{Status: "no"}
	}
}
