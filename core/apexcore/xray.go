package apexcore

// Второе ядро — Xray (как в Happ и v2rayNG). Нужно для того, что sing-box не умеет:
// XHTTP (SplitHTTP), mKCP, VLESS Encryption (ML-KEM) и т. п. Xray запускается
// приложением отдельным процессом; sing-box держит TUN и маршрутизацию, а такой
// сервер видит как SOCKS-прокси на 127.0.0.1. Чтобы трафик Xray к серверу не
// зациклился в туннель, IP серверов Xray в sing-box всегда идут напрямую.

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"sort"
	"strings"
	"time"
)

// XrayBasePort — первый локальный порт SOCKS для серверов Xray.
const XrayBasePort = 10910

// ─── Ссылки → outbound Xray ─────────────────────────────────────────────

// params — параметры ссылки (query vless:// или поля vmess JSON).
type params map[string]string

func (p params) get(k string) string { return strings.TrimSpace(p[k]) }

func fromQuery(q url.Values) params {
	p := params{}
	for k, v := range q {
		if len(v) > 0 {
			p[k] = v[0]
		}
	}
	return p
}

// старые имена заголовков mKCP → mkcp-legacy
var kcpHeaders = map[string]string{
	"none": "", "srtp": "srtp", "utp": "utp", "wechat-video": "wechat", "wechat": "wechat",
	"dtls": "dtls", "wireguard": "wireguard", "dns": "dns",
}

var xrayNetworks = map[string]string{
	"": "tcp", "tcp": "tcp", "raw": "tcp", "kcp": "kcp", "mkcp": "kcp", "ws": "ws", "websocket": "ws",
	"grpc": "grpc", "gun": "grpc", "httpupgrade": "httpupgrade", "xhttp": "xhttp", "splithttp": "xhttp",
}

// xrayStream строит streamSettings по стандартным параметрам ссылок Xray.
func xrayStream(p params, server string, defaultSecurity string) (map[string]any, error) {
	rawNet := strings.ToLower(p.get("type"))
	network, ok := xrayNetworks[rawNet]
	if !ok {
		return nil, fmt.Errorf("транспорт %s не поддерживается Xray", rawNet)
	}
	s := map[string]any{"network": network}
	path, host := p.get("path"), p.get("host")
	switch network {
	case "tcp":
		if strings.EqualFold(p.get("headerType"), "http") {
			req := map[string]any{"path": []string{firstNonEmpty(path, "/")}}
			if host != "" {
				req["headers"] = map[string]any{"Host": splitList(host)}
			}
			s["tcpSettings"] = map[string]any{"header": map[string]any{"type": "http", "request": req}}
		}
	case "kcp":
		// В Xray 26 заголовок и seed mKCP перенесены в finalmask. Если ссылка не
		// задаёт finalmask (fm), повторяем старый формат: [заголовок][шифрование].
		s["kcpSettings"] = map[string]any{}
		if p.get("fm") == "" {
			var masks []any
			if h := kcpHeaders[strings.ToLower(firstNonEmpty(p.get("headerType"), "none"))]; h != "" {
				hs := map[string]any{"header": h}
				if h == "dns" && host != "" {
					hs["value"] = host
				}
				masks = append(masks, map[string]any{"type": "mkcp-legacy", "settings": hs})
			}
			legacy := map[string]any{}
			if seed := firstNonEmpty(p.get("seed"), p.get("kcpSeed")); seed != "" {
				legacy["value"] = seed
			}
			masks = append(masks, map[string]any{"type": "mkcp-legacy", "settings": legacy})
			s["finalmask"] = map[string]any{"udp": masks}
		}
	case "ws":
		ws := map[string]any{"path": firstNonEmpty(path, "/")}
		if host != "" {
			ws["host"] = host
		}
		s["wsSettings"] = ws
	case "httpupgrade":
		hu := map[string]any{"path": firstNonEmpty(path, "/")}
		if host != "" {
			hu["host"] = host
		}
		s["httpupgradeSettings"] = hu
	case "grpc":
		g := map[string]any{"serviceName": firstNonEmpty(p.get("serviceName"), strings.TrimPrefix(path, "/"))}
		if strings.EqualFold(p.get("mode"), "multi") {
			g["multiMode"] = true
		}
		if a := p.get("authority"); a != "" {
			g["authority"] = a
		}
		s["grpcSettings"] = g
	case "xhttp":
		x := map[string]any{"path": firstNonEmpty(path, "/"), "mode": firstNonEmpty(p.get("mode"), "auto")}
		if host != "" {
			x["host"] = host
		}
		if extra := p.get("extra"); extra != "" {
			var e map[string]any
			if err := json.Unmarshal([]byte(extra), &e); err != nil {
				return nil, errors.New("неверный параметр extra у XHTTP")
			}
			x["extra"] = e
		}
		s["xhttpSettings"] = x
	}

	if fm := p.get("fm"); fm != "" {
		var m map[string]any
		if err := json.Unmarshal([]byte(fm), &m); err != nil {
			return nil, errors.New("неверный параметр fm (finalmask)")
		}
		s["finalmask"] = m
	}

	security := strings.ToLower(firstNonEmpty(p.get("security"), defaultSecurity))
	sni := firstNonEmpty(p.get("sni"), p.get("peer"))
	fp := p.get("fp")
	switch security {
	case "", "none":
		s["security"] = "none"
	case "tls", "xtls":
		t := map[string]any{}
		if sni == "" && net.ParseIP(server) == nil {
			sni = server
		}
		if sni != "" {
			t["serverName"] = sni
		}
		if fp != "" && fp != "none" {
			t["fingerprint"] = fp
		} else if fp == "" {
			t["fingerprint"] = "chrome"
		}
		if a := splitList(p.get("alpn")); len(a) > 0 {
			t["alpn"] = a
		}
		// allowInsecure в Xray 26 удалён: вместо него закрепление сертификата
		if pcs := p.get("pcs"); pcs != "" {
			t["pinnedPeerCertSha256"] = pcs
		}
		if vcn := p.get("vcn"); vcn != "" {
			t["verifyPeerCertByName"] = vcn
		}
		if ech := p.get("ech"); ech != "" {
			t["echConfigList"] = ech
		}
		s["security"] = "tls"
		s["tlsSettings"] = t
	case "reality":
		pbk := p.get("pbk")
		if pbk == "" {
			return nil, errors.New("у Reality нет открытого ключа (pbk)")
		}
		r := map[string]any{
			"serverName":  sni,
			"fingerprint": firstNonEmpty(fp, "chrome"),
			"publicKey":   pbk,
			"shortId":     p.get("sid"),
		}
		if spx := p.get("spx"); spx != "" {
			r["spiderX"] = spx
		}
		if pqv := p.get("pqv"); pqv != "" {
			r["mldsa65Verify"] = pqv
		}
		s["security"] = "reality"
		s["realitySettings"] = r
	default:
		return nil, fmt.Errorf("защита %s не поддерживается", security)
	}
	return s, nil
}

// xrayFromLink разбирает ссылку в outbound Xray. Возвращает nil без ошибки для
// протоколов, которые Xray не нужен (Hysteria, TUIC, WireGuard и т. п.).
func xrayFromLink(link string) (*Node, error) {
	scheme, _, _ := strings.Cut(link, "://")
	switch strings.ToLower(scheme) {
	case "vless", "trojan":
		return xrayVLESSOrTrojan(link)
	case "vmess":
		return xrayVMess(link)
	case "ss":
		return xrayShadowsocks(link)
	}
	return nil, nil
}

func xrayVLESSOrTrojan(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, err
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, err
	}
	name := fragmentName(u)
	p := fromQuery(u.Query())
	proto := strings.ToLower(u.Scheme)
	var settings map[string]any
	defSec := ""
	if proto == "vless" {
		user := map[string]any{"id": u.User.Username(), "encryption": firstNonEmpty(p.get("encryption"), "none")}
		if user["id"] == "" {
			return nil, fmt.Errorf("vless %s: нет UUID", name)
		}
		if flow := p.get("flow"); flow != "" {
			user["flow"] = flow
		}
		settings = map[string]any{"vnext": []any{map[string]any{"address": server, "port": port, "users": []any{user}}}}
	} else {
		defSec = "tls"
		settings = map[string]any{"servers": []any{map[string]any{"address": server, "port": port, "password": u.User.Username()}}}
	}
	stream, err := xrayStream(p, server, defSec)
	if err != nil {
		return nil, fmt.Errorf("%s %s: %w", proto, name, err)
	}
	// Xray 26 не запускает VLESS/Trojan без TLS и без VLESS Encryption на публичных адресах
	if stream["security"] == "none" && !isPrivateHost(server) {
		enc := p.get("encryption")
		if proto == "trojan" || enc == "" || enc == "none" {
			return nil, fmt.Errorf("%s %s: без TLS Xray 26 такой сервер не подключает", proto, name)
		}
	}
	ob := map[string]any{"protocol": proto, "settings": settings, "streamSettings": stream}
	return &Node{Name: name, Type: proto, Server: server, Port: port, Xray: ob}, nil
}

func isPrivateHost(host string) bool {
	if ip := net.ParseIP(host); ip != nil {
		return ip.IsPrivate() || ip.IsLoopback() || ip.IsLinkLocalUnicast()
	}
	h := strings.ToLower(host)
	return h == "localhost" || strings.HasSuffix(h, ".local") || strings.HasSuffix(h, ".lan")
}

func xrayVMess(link string) (*Node, error) {
	raw, ok := decodeBase64(strings.TrimPrefix(link, "vmess://"))
	if !ok {
		return nil, errors.New("vmess: не base64")
	}
	var v vmessJSON
	if err := json.Unmarshal([]byte(raw), &v); err != nil {
		return nil, fmt.Errorf("vmess: %w", err)
	}
	port := rawInt(v.Port)
	if v.Add == "" || port <= 0 {
		return nil, fmt.Errorf("vmess %s: неверный адрес", v.PS)
	}
	p := params{
		"type": v.Net, "headerType": v.Type, "host": v.Host, "path": v.Path,
		"security": v.TLS, "sni": v.SNI, "alpn": v.ALPN, "fp": v.FP,
	}
	switch strings.ToLower(v.Net) {
	case "grpc":
		p["serviceName"], p["path"] = v.Path, ""
		if v.Type == "multi" {
			p["mode"] = "multi"
		}
	case "kcp":
		p["seed"] = v.Path
	case "xhttp", "splithttp":
		p["mode"] = v.Type
		if v.Type == "none" {
			p["mode"] = ""
		}
	}
	stream, err := xrayStream(p, v.Add, "")
	if err != nil {
		return nil, fmt.Errorf("vmess %s: %w", v.PS, err)
	}
	user := map[string]any{"id": v.ID, "alterId": rawInt(v.Aid), "security": firstNonEmpty(v.Scy, "auto")}
	ob := map[string]any{
		"protocol":       "vmess",
		"settings":       map[string]any{"vnext": []any{map[string]any{"address": v.Add, "port": port, "users": []any{user}}}},
		"streamSettings": stream,
	}
	return &Node{Name: strings.TrimSpace(v.PS), Type: "vmess", Server: v.Add, Port: port, Xray: ob}, nil
}

func xrayShadowsocks(link string) (*Node, error) {
	n, err := parseShadowsocks(link)
	if err != nil {
		return nil, err
	}
	if _, plugin := n.Outbound["plugin"]; plugin {
		return nil, nil // плагины SIP003 есть только в sing-box
	}
	srv := map[string]any{"address": n.Server, "port": n.Port, "method": n.Outbound["method"], "password": n.Outbound["password"]}
	ob := map[string]any{"protocol": "shadowsocks", "settings": map[string]any{"servers": []any{srv}}}
	return &Node{Name: n.Name, Type: "shadowsocks", Server: n.Server, Port: n.Port, Xray: ob}, nil
}

// xrayDetail — описание для узла, который работает только через Xray.
func xrayDetail(ob map[string]any) string {
	var parts []string
	stream, _ := ob["streamSettings"].(map[string]any)
	switch stream["security"] {
	case "reality":
		parts = append(parts, "Reality")
	case "tls":
		parts = append(parts, "TLS")
	}
	if s, _ := ob["settings"].(map[string]any); s != nil {
		if vn, _ := s["vnext"].([]any); len(vn) > 0 {
			if users, _ := vn[0].(map[string]any)["users"].([]any); len(users) > 0 {
				u, _ := users[0].(map[string]any)
				if f, _ := u["flow"].(string); f != "" {
					parts = append(parts, "Vision")
				}
				if e, _ := u["encryption"].(string); e != "" && e != "none" {
					parts = append(parts, "Encryption")
				}
			}
		}
	}
	names := map[string]string{"tcp": "TCP", "kcp": "mKCP", "ws": "WS", "grpc": "gRPC", "httpupgrade": "HTTPUpgrade", "xhttp": "XHTTP"}
	if n, ok := names[fmt.Sprint(stream["network"])]; ok {
		parts = append(parts, n)
	} else if ob["protocol"] != "shadowsocks" {
		parts = append(parts, "TCP")
	}
	parts = append(parts, "Xray")
	return strings.Join(parts, " · ")
}

// ─── Сборка конфига Xray ────────────────────────────────────────────────

// xrayAddress возвращает объект с адресом сервера внутри outbound Xray.
func xrayAddress(ob map[string]any) map[string]any {
	s, _ := ob["settings"].(map[string]any)
	for _, key := range []string{"vnext", "servers"} {
		if list, _ := s[key].([]any); len(list) > 0 {
			if m, ok := list[0].(map[string]any); ok {
				return m
			}
		}
	}
	return nil
}

// pinToIP подставляет IP вместо домена, сохраняя SNI и Host. Нужно, чтобы
// sing-box точно знал, какие IP пускать мимо туннеля.
func pinToIP(ob map[string]any, domain, ip string) {
	if addr := xrayAddress(ob); addr != nil {
		addr["address"] = ip
	}
	stream, _ := ob["streamSettings"].(map[string]any)
	if stream == nil {
		return
	}
	if t, _ := stream["tlsSettings"].(map[string]any); t != nil {
		if _, ok := t["serverName"]; !ok {
			t["serverName"] = domain
		}
	}
	for _, key := range []string{"wsSettings", "httpupgradeSettings", "xhttpSettings"} {
		if t, _ := stream[key].(map[string]any); t != nil {
			if h, _ := t["host"].(string); h == "" {
				t["host"] = domain
			}
		}
	}
	if t, _ := stream["grpcSettings"].(map[string]any); t != nil {
		if a, _ := t["authority"].(string); a == "" && stream["security"] != "reality" {
			t["authority"] = domain
		}
	}
}

// lookupIPs — разрешение домена сервера; переопределяется в тестах.
var lookupIPs = func(host string) []string {
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()
	addrs, err := net.DefaultResolver.LookupIPAddr(ctx, host)
	if err != nil {
		return nil
	}
	var v4, v6 []string
	for _, a := range addrs {
		if a.IP.To4() != nil {
			v4 = append(v4, a.IP.String())
		} else {
			v6 = append(v6, a.IP.String())
		}
	}
	// IPv4 первым: Xray подключается к первому адресу, а IPv6 есть не у всех
	sort.Strings(v4)
	sort.Strings(v6)
	return append(v4, v6...)
}

func randomToken() string {
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

type xrayPlan struct {
	inbounds  []any
	outbounds []any
	rules     []any
	directIPs []string
	direct    []string // домены, которые не удалось разрешить
	user      string
	pass      string
	basePort  int
	resolved  map[string][]string // один домен — одно разрешение на сборку конфига
	// dialPort — SOCKS sing-box «xray-dial» на 127.0.0.1: через него Xray выходит в сеть
	// (защищёнными сокетами sing-box, мимо туннеля). Без него соединения Xray шли в сам
	// туннель и второй раз через sing-box — лишняя работа и заметно ниже скорость. 0 — выкл.
	dialPort int
}

const xrayDialTag = "apex-dial"

func newXrayPlan(auth string) *xrayPlan {
	if auth == "" {
		auth = randomToken()
	}
	sum := base64.RawURLEncoding.EncodeToString([]byte(auth))
	if len(sum) > 16 {
		sum = sum[:16]
	}
	return &xrayPlan{user: "apex", pass: sum, basePort: XrayBasePort, resolved: map[string][]string{}}
}

// add переводит узел на Xray и возвращает SOCKS-outbound для sing-box.
func (x *xrayPlan) add(n *Node) map[string]any {
	idx := len(x.inbounds)
	port := x.basePort + idx
	in := fmt.Sprintf("in-%d", idx)
	out := fmt.Sprintf("out-%d", idx)

	ob := deepCopy(n.Xray).(map[string]any)
	ob["tag"] = out
	if x.dialPort > 0 {
		stream, _ := ob["streamSettings"].(map[string]any)
		if stream == nil {
			stream = map[string]any{}
			ob["streamSettings"] = stream
		}
		so, _ := stream["sockopt"].(map[string]any)
		if so == nil {
			so = map[string]any{}
			stream["sockopt"] = so
		}
		if _, ok := so["dialerProxy"]; !ok {
			if _, chained := ob["proxySettings"]; !chained {
				so["dialerProxy"] = xrayDialTag
			}
		}
	}
	if net.ParseIP(n.Server) != nil {
		x.directIPs = append(x.directIPs, n.Server)
	} else if ips := x.lookup(n.Server); len(ips) > 0 {
		x.directIPs = append(x.directIPs, ips...)
		pinToIP(ob, n.Server, ips[0])
	} else {
		x.direct = append(x.direct, n.Server)
	}
	x.outbounds = append(x.outbounds, ob)
	x.inbounds = append(x.inbounds, map[string]any{
		"tag": in, "listen": "127.0.0.1", "port": port, "protocol": "socks",
		"settings": map[string]any{
			"auth": "password", "udp": true, "ip": "127.0.0.1",
			"accounts": []any{map[string]any{"user": x.user, "pass": x.pass}},
		},
	})
	x.rules = append(x.rules, map[string]any{"type": "field", "inboundTag": []string{in}, "outboundTag": out})
	return map[string]any{
		"type": "socks", "tag": n.Tag, "server": "127.0.0.1", "server_port": port, "version": "5",
		"username": x.user, "password": x.pass,
	}
}

func (x *xrayPlan) lookup(host string) []string {
	if ips, ok := x.resolved[host]; ok {
		return ips
	}
	ips := lookupIPs(host)
	x.resolved[host] = ips
	return ips
}

// singBoxRules — правила sing-box, чтобы соединения Xray к серверам шли мимо туннеля.
func (x *xrayPlan) singBoxRules() []any {
	var rules []any
	if len(x.directIPs) > 0 {
		var cidrs []string
		seen := map[string]bool{}
		for _, ip := range x.directIPs {
			if seen[ip] {
				continue
			}
			seen[ip] = true
			if strings.Contains(ip, ":") {
				cidrs = append(cidrs, ip+"/128")
			} else {
				cidrs = append(cidrs, ip+"/32")
			}
		}
		rules = append(rules, map[string]any{"ip_cidr": cidrs, "outbound": "direct"})
	}
	if len(x.direct) > 0 {
		rules = append(rules, map[string]any{"domain": x.direct, "outbound": "direct"})
	}
	return rules
}

func (x *xrayPlan) config() string {
	if len(x.inbounds) == 0 {
		return ""
	}
	cfg := map[string]any{
		"log":       map[string]any{"loglevel": "warning"},
		"dns":       map[string]any{"servers": []string{"1.1.1.1", "8.8.8.8"}},
		"inbounds":  x.inbounds,
		"outbounds": x.allOutbounds(),
		"routing":   map[string]any{"domainStrategy": "AsIs", "rules": x.rules},
	}
	out, _ := json.MarshalIndent(cfg, "", "  ")
	return string(out)
}

func (x *xrayPlan) allOutbounds() []any {
	all := append(append([]any{}, x.outbounds...), map[string]any{"protocol": "freedom", "tag": "direct"})
	if x.dialPort > 0 {
		all = append(all, map[string]any{
			"protocol": "socks", "tag": xrayDialTag,
			"settings": map[string]any{"servers": []any{map[string]any{
				"address": "127.0.0.1", "port": x.dialPort,
				"users": []any{map[string]any{"user": x.user, "pass": x.pass}},
			}}},
		})
	}
	return all
}

// dialInbound — вход sing-box для исходящих соединений Xray (nil, если Xray не нужен).
func (x *xrayPlan) dialInbound() map[string]any {
	if x.dialPort <= 0 || len(x.inbounds) == 0 {
		return nil
	}
	return map[string]any{
		"type": "socks", "tag": xrayDialTag, "listen": "127.0.0.1", "listen_port": x.dialPort,
		"users": []any{map[string]any{"username": x.user, "password": x.pass}},
	}
}

func deepCopy(v any) any {
	switch t := v.(type) {
	case map[string]any:
		m := make(map[string]any, len(t))
		for k, val := range t {
			m[k] = deepCopy(val)
		}
		return m
	case []any:
		l := make([]any, len(t))
		for i, val := range t {
			l[i] = deepCopy(val)
		}
		return l
	case []string:
		return append([]string{}, t...)
	default:
		return v
	}
}
