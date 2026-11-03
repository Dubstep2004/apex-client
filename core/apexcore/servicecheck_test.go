package apexcore

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestServiceParsers(t *testing.T) {
	if r := chatgptResult(403, `{"cf_details":"unsupported_country"}`, nil); r.Status != "no" {
		t.Fatal("chatgpt blocked", r)
	}
	if r := chatgptResult(200, `{"requirements":[]}`, nil); r.Status != "yes" {
		t.Fatal("chatgpt ok", r)
	}
	if r := youtubeResult(200, `..."INNERTUBE_CONTEXT_GL":"NL"... ad-free`, nil); r.Status != "yes" || r.Region != "NL" {
		t.Fatal("yt ok", r)
	}
	if r := youtubeResult(200, `YouTube Premium is not available in your country`, nil); r.Status != "no" {
		t.Fatal("yt no", r)
	}
	if r := netflixResult(200, nil, 404, nil, ""); r.Status != "partial" {
		t.Fatal("nf partial", r)
	}
	if r := netflixResult(301, nil, 301, nil, "https://www.netflix.com/de-en/title/70143836"); r.Status != "yes" || r.Region != "DE" {
		t.Fatal("nf yes", r)
	}
	if r := netflixResult(404, nil, 404, nil, ""); r.Status != "no" {
		t.Fatal("nf no", r)
	}
	if r := geminiResult(200, `x 45631641,null,true y`, nil); r.Status != "yes" {
		t.Fatal("gemini", r)
	}
	if traceCountry("ip=1.2.3.4\nloc=fi\nwarp=off\n") != "FI" {
		t.Fatal("trace")
	}
}

func TestServiceCheckFlow(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/trace", func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, "loc=NL\n") })
	mux.HandleFunc("/openai", func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, `{"ok":true}`) })
	mux.HandleFunc("/yt", func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, `"countryCode":"NL"`) })
	mux.HandleFunc("/nf1", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(200) })
	mux.HandleFunc("/nf2", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(404) })
	mux.HandleFunc("/gem", func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, "/unavailable", 302) })
	srv := httptest.NewServer(mux)
	defer srv.Close()
	old := []*string{&urlTrace, &urlOpenAI, &urlYouTube, &urlNetflix1, &urlNetflix2, &urlGemini}
	saved := make([]string, len(old))
	for i, p := range old {
		saved[i] = *p
	}
	defer func() {
		for i, p := range old {
			*p = saved[i]
		}
	}()
	urlTrace, urlOpenAI, urlYouTube = srv.URL+"/trace", srv.URL+"/openai", srv.URL+"/yt"
	urlNetflix1, urlNetflix2, urlGemini = srv.URL+"/nf1", srv.URL+"/nf2", srv.URL+"/gem"
	var d net.Dialer
	out := runServiceCheck(func(ctx context.Context, n, a string) (net.Conn, error) { return d.DialContext(ctx, n, a) }, 3*time.Second)
	var r serviceReport
	if err := json.Unmarshal([]byte(out), &r); err != nil {
		t.Fatal(err, out)
	}
	if r.Country != "NL" || r.ChatGPT.Status != "yes" || r.ChatGPT.Region != "NL" || r.YouTube.Status != "yes" ||
		r.Netflix.Status != "partial" || r.Gemini.Status != "no" {
		t.Fatalf("unexpected: %s", out)
	}
	if ServiceCheck("x", 1000) != `{"error":"VPN выключен"}` {
		t.Fatal("must refuse without probe proxy")
	}
}

func TestServiceCountryRules(t *testing.T) {
	r := serviceReport{Country: "RU", ChatGPT: serviceResult{Status: "yes"}, Gemini: serviceResult{Status: "yes"},
		YouTube: serviceResult{Status: "yes"}, Netflix: serviceResult{Status: "partial"}}
	applyCountryRules(&r)
	if r.ChatGPT.Status != "no" || r.Gemini.Status != "no" || r.YouTube.Status != "no" || r.Netflix.Status != "no" {
		t.Fatalf("RU must be no: %+v", r)
	}
	r = serviceReport{Country: "NL", ChatGPT: serviceResult{Status: "yes"}}
	applyCountryRules(&r)
	if r.ChatGPT.Status != "yes" {
		t.Fatal("NL must stay yes")
	}
	if youtubeResult(200, "<html>consent</html>", nil).Status != "error" {
		t.Fatal("page without markers must be error")
	}
}
