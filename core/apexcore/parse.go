// Package apexcore converts a subscription from sub.knownhosts.xyz into
// sing-box outbounds and a ready-to-run sing-box config for APEX CLIENT.
// It is bound to Android together with libbox via gomobile, so the exported
// API uses only strings, int32, bool and error.
package apexcore

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// Node is one server from the subscription.
type Node struct {
	Tag      string         `json:"tag"`
	Name     string         `json:"name"`
	Type     string         `json:"type"`
	Server   string         `json:"server"`
	Port     int            `json:"port"`
	Outbound map[string]any `json:"outbound"`
	// Endpoint — узел уровня endpoints в sing-box (WireGuard), а не outbounds
	Endpoint bool `json:"endpoint,omitempty"`
	// Категория протокола для экрана выбора: VLESS, Hysteria 2, ShadowTLS…
	Category string `json:"category,omitempty"`
	// Описание подключения: «Reality · Vision · TCP»
	Detail string `json:"detail,omitempty"`
	// UDP — протокол поверх UDP/QUIC (задержку не измерить по TCP)
	UDP bool `json:"udp,omitempty"`
	// Extra — промежуточные outbounds цепочки (detour): ShadowTLS и т. п.
	Extra []map[string]any `json:"extra,omitempty"`
	// Xray — тот же сервер в формате Xray (второе ядро). Если Outbound пуст,
	// сервер работает только через Xray (XHTTP, mKCP, VLESS Encryption…)
	Xray map[string]any `json:"xray,omitempty"`
	// Description — подпись под названием (serverDescription, как в Happ)
	Description string `json:"description,omitempty"`
	// Ads — в описании или названии есть пометка о рекламе
	Ads bool `json:"ads,omitempty"`
}

type parseResult struct {
	Nodes   []Node   `json:"nodes"`
	Skipped []string `json:"skipped"`
	// Meta — заголовки подписки, переданные в теле строками «#announce: …»
	Meta map[string]string `json:"meta,omitempty"`
}

// ParseSubscription accepts the raw body of a subscription response
// (base64 list of links, plain list of links, or a sing-box JSON config)
// and returns JSON: {"nodes":[...],"skipped":["reason", ...]}.
func ParseSubscription(content string) (string, error) {
	res, err := parse(content)
	if err != nil {
		return "", err
	}
	out, err := json.Marshal(res)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

func parse(content string) (*parseResult, error) {
	content = strings.TrimSpace(strings.TrimPrefix(content, string(rune(0xFEFF))))
	if content == "" {
		return nil, errors.New("подписка пуста")
	}
	res := &parseResult{Nodes: []Node{}, Skipped: []string{}}

	if isXrayJSON(content) {
		// Xray JSON (как у Happ): полные конфиги Xray
		if err := parseXrayJSON(content, res); err != nil {
			return nil, err
		}
	} else if strings.HasPrefix(content, "{") {
		if err := parseSingBoxJSON(content, res); err != nil {
			return nil, err
		}
	} else {
		if !strings.Contains(content, "://") {
			if decoded, ok := decodeBase64(content); ok {
				content = decoded
			}
		}
		if isXrayJSON(content) {
			if err := parseXrayJSON(content, res); err != nil {
				return nil, err
			}
		} else if strings.HasPrefix(strings.TrimSpace(content), "{") {
			if err := parseSingBoxJSON(content, res); err != nil {
				return nil, err
			}
		} else {
			for _, line := range strings.Split(content, "\n") {
				line = strings.TrimSpace(line)
				if k, v, ok := metaLine(line); ok {
					if res.Meta == nil {
						res.Meta = map[string]string{}
					}
					res.Meta[k] = v
					continue
				}
				if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "//") {
					continue
				}
				node, err := parseLink(line)
				xn, xerr := xrayFromLink(line)
				switch {
				case err == nil && xn != nil:
					node.Xray = xn.Xray
				case err != nil && xn != nil:
					node, err = xn, nil // sing-box не умеет — подключим через Xray
				case err != nil && xerr != nil:
					err = fmt.Errorf("%v; %v", err, xerr)
				}
				if err != nil {
					res.Skipped = append(res.Skipped, err.Error())
					continue
				}
				res.Nodes = append(res.Nodes, *node)
			}
		}
	}

	if len(res.Nodes) == 0 {
		if len(res.Skipped) > 0 {
			return nil, fmt.Errorf("нет поддерживаемых серверов: %s", strings.Join(res.Skipped, "; "))
		}
		return nil, errors.New("в подписке не найдено серверов")
	}
	names := map[string]int{}
	for i := range res.Nodes {
		n := &res.Nodes[i]
		splitDescription(n)
		n.Tag = fmt.Sprintf("node-%d", i+1)
		if n.Outbound != nil {
			n.Outbound["tag"] = n.Tag
			linkChain(n)
		}
		fillMeta(n)
		if strings.TrimSpace(n.Name) == "" {
			n.Name = fmt.Sprintf("%s:%d", n.Server, n.Port)
		}
		names[n.Name]++
		if names[n.Name] > 1 {
			n.Name = fmt.Sprintf("%s (%d)", n.Name, names[n.Name])
		}
	}
	return res, nil
}

func decodeBase64(s string) (string, bool) {
	s = strings.Join(strings.Fields(s), "")
	for _, enc := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
		if b, err := enc.DecodeString(s); err == nil {
			return string(b), true
		}
	}
	return "", false
}

var proxyTypes = map[string]bool{
	"vless": true, "vmess": true, "trojan": true, "shadowsocks": true,
	"hysteria2": true, "tuic": true, "hysteria": true, "anytls": true,
	"naive": true, "socks": true, "http": true, "ssh": true, "shadowtls": true,
}

const maxChain = 3

func parseSingBoxJSON(content string, res *parseResult) error {
	var cfg struct {
		Outbounds []map[string]any `json:"outbounds"`
		Endpoints []map[string]any `json:"endpoints"`
	}
	if err := json.Unmarshal([]byte(content), &cfg); err != nil {
		return fmt.Errorf("неверный JSON подписки: %w", err)
	}
	byTag := map[string]map[string]any{}
	for _, ob := range cfg.Outbounds {
		if tag, _ := ob["tag"].(string); tag != "" {
			byTag[tag] = ob
		}
	}
	for _, ob := range cfg.Outbounds {
		typ, _ := ob["type"].(string)
		if !proxyTypes[typ] {
			continue
		}
		name, _ := ob["tag"].(string)
		// ShadowTLS сам по себе не прокси — он нужен как ступень цепочки
		if typ == "shadowtls" {
			continue
		}
		extra, err := resolveChain(ob, byTag)
		if err != nil {
			res.Skipped = append(res.Skipped, fmt.Sprintf("%s: %v", name, err))
			continue
		}
		main := copyMap(ob)
		server, _ := main["server"].(string)
		port := 0
		if p, ok := main["server_port"].(float64); ok {
			port = int(p)
		}
		desc := ""
		if m, ok := main["meta"].(map[string]any); ok {
			desc, _ = m["serverDescription"].(string)
			delete(main, "meta")
		}
		res.Nodes = append(res.Nodes, Node{Name: name, Type: typ, Server: server, Port: port, Outbound: main, Extra: extra, Description: desc})
	}
	for _, ep := range cfg.Endpoints {
		if typ, _ := ep["type"].(string); typ != "wireguard" {
			continue
		}
		name, _ := ep["tag"].(string)
		var server string
		var port int
		if peers, ok := ep["peers"].([]any); ok && len(peers) > 0 {
			if p, ok := peers[0].(map[string]any); ok {
				server, _ = p["address"].(string)
				if f, ok := p["port"].(float64); ok {
					port = int(f)
				}
			}
		}
		delete(ep, "detour")
		res.Nodes = append(res.Nodes, Node{Name: name, Type: "wireguard", Server: server, Port: port, Outbound: ep, Endpoint: true})
	}
	return nil
}

func copyMap(m map[string]any) map[string]any {
	c := make(map[string]any, len(m))
	for k, v := range m {
		c[k] = v
	}
	return c
}

// resolveChain собирает цепочку detour (например, Shadowsocks → ShadowTLS).
// Ссылки на direct/неизвестные теги отбрасываются — узел подключается напрямую.
func resolveChain(ob map[string]any, byTag map[string]map[string]any) ([]map[string]any, error) {
	var chain []map[string]any
	seen := map[string]bool{}
	cur := ob
	for {
		detour, _ := cur["detour"].(string)
		if detour == "" {
			break
		}
		next, ok := byTag[detour]
		typ, _ := next["type"].(string)
		if !ok || !proxyTypes[typ] {
			break // direct, selector и т. п. — просто идём напрямую
		}
		if seen[detour] || len(chain) >= maxChain {
			return nil, errors.New("слишком длинная или зацикленная цепочка")
		}
		seen[detour] = true
		chain = append(chain, copyMap(next))
		cur = next
	}
	return chain, nil
}

// linkChain даёт ступеням цепочки уникальные теги и связывает их через detour.
func linkChain(n *Node) {
	prev := n.Outbound
	for j, e := range n.Extra {
		tag := fmt.Sprintf("%s-via%d", n.Tag, j+1)
		e["tag"] = tag
		prev["detour"] = tag
		prev = e
	}
	delete(prev, "detour")
}

func parseLink(link string) (*Node, error) {
	scheme, _, ok := strings.Cut(link, "://")
	if !ok {
		return nil, fmt.Errorf("не ссылка: %.20s…", link)
	}
	switch strings.ToLower(scheme) {
	case "vless":
		return parseVLESS(link)
	case "vmess":
		return parseVMess(link)
	case "trojan":
		return parseTrojan(link)
	case "ss":
		return parseShadowsocks(link)
	case "hysteria2", "hy2":
		return parseHysteria2(link)
	case "tuic":
		return parseTUIC(link)
	case "wireguard", "wg":
		return parseWireGuard(link)
	case "hysteria", "hy":
		return parseHysteria(link)
	case "socks", "socks5", "socks5h", "socks4", "socks4a":
		return parseSOCKS(link)
	case "http", "https":
		return parseHTTPProxy(link)
	case "ssh":
		return parseSSH(link)
	case "anytls":
		return parseAnyTLS(link)
	case "naive+https", "naive+quic", "naive":
		return parseNaive(link)
	case "ssr":
		return parseSSR(link)
	case "xhttp", "splithttp":
		return nil, errors.New("XHTTP не поддерживается ядром sing-box")
	case "awg", "amneziawg":
		return nil, errors.New("AmneziaWG не поддерживается ядром sing-box")
	default:
		return nil, fmt.Errorf("протокол %s не поддерживается", scheme)
	}
}

func hostPort(u *url.URL) (string, int, error) {
	host := u.Hostname()
	port, err := strconv.Atoi(u.Port())
	if host == "" || err != nil || port <= 0 || port > 65535 {
		return "", 0, fmt.Errorf("неверный адрес сервера %q", u.Host)
	}
	return host, port, nil
}

func fragmentName(u *url.URL) string {
	name := u.Fragment
	if n, err := url.PathUnescape(name); err == nil {
		name = n
	}
	return strings.TrimSpace(name)
}

func splitList(s string) []string {
	var out []string
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func truthy(s string) bool {
	s = strings.ToLower(strings.TrimSpace(s))
	return s == "1" || s == "true" || s == "yes"
}

// tlsOptions builds a sing-box tls block. security is "tls", "reality" or "".
func tlsOptions(security, sni, fp, alpn, pbk, sid string, insecure bool, server string) map[string]any {
	security = strings.ToLower(security)
	if security != "tls" && security != "reality" && security != "xtls" {
		return nil
	}
	tls := map[string]any{"enabled": true}
	if sni == "" && net.ParseIP(server) == nil {
		sni = server
	}
	if sni != "" {
		tls["server_name"] = sni
	}
	if insecure {
		tls["insecure"] = true
	}
	if a := splitList(alpn); len(a) > 0 {
		tls["alpn"] = a
	}
	if fp == "" && security == "reality" {
		fp = "chrome"
	}
	if fp != "" && fp != "none" {
		if fp == "random" || fp == "randomized" {
			fp = "randomized"
		}
		tls["utls"] = map[string]any{"enabled": true, "fingerprint": fp}
	}
	if security == "reality" {
		tls["reality"] = map[string]any{"enabled": true, "public_key": pbk, "short_id": sid}
	}
	return tls
}

// transportOptions builds a sing-box v2ray transport. Returns (nil, nil) for raw TCP.
func transportOptions(network, path, host, serviceName, headerType string) (map[string]any, error) {
	switch strings.ToLower(network) {
	case "", "tcp", "raw":
		if strings.EqualFold(headerType, "http") {
			t := map[string]any{"type": "http", "method": "GET"}
			if host != "" {
				t["host"] = splitList(host)
			}
			if path != "" {
				t["path"] = path
			}
			return t, nil
		}
		return nil, nil
	case "ws", "websocket":
		t := map[string]any{"type": "ws"}
		if p, ed, ok := strings.Cut(path, "?ed="); ok {
			path = p
			if n, err := strconv.Atoi(ed); err == nil && n > 0 {
				t["max_early_data"] = n
				t["early_data_header_name"] = "Sec-WebSocket-Protocol"
			}
		}
		if path != "" {
			t["path"] = path
		}
		if host != "" {
			t["headers"] = map[string]any{"Host": host}
		}
		return t, nil
	case "grpc":
		t := map[string]any{"type": "grpc"}
		if serviceName == "" {
			serviceName = strings.TrimPrefix(path, "/")
		}
		if serviceName != "" {
			t["service_name"] = serviceName
		}
		return t, nil
	case "http", "h2":
		t := map[string]any{"type": "http"}
		if host != "" {
			t["host"] = splitList(host)
		}
		if path != "" {
			t["path"] = path
		}
		return t, nil
	case "httpupgrade":
		t := map[string]any{"type": "httpupgrade"}
		if host != "" {
			t["host"] = host
		}
		if path != "" {
			t["path"] = path
		}
		return t, nil
	case "quic":
		return map[string]any{"type": "quic"}, nil
	default:
		return nil, fmt.Errorf("транспорт %s не поддерживается", network)
	}
}

func parseVLESS(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("vless: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("vless: %w", err)
	}
	name := fragmentName(u)
	q := u.Query()
	uuid := u.User.Username()
	if uuid == "" {
		return nil, fmt.Errorf("vless %s: нет UUID", name)
	}
	ob := map[string]any{
		"type":            "vless",
		"server":          server,
		"server_port":     port,
		"uuid":            uuid,
		"packet_encoding": "xudp",
	}
	if enc := q.Get("encryption"); enc != "" && enc != "none" {
		return nil, fmt.Errorf("vless %s: VLESS Encryption не поддерживается sing-box", name)
	}
	if flow := q.Get("flow"); flow != "" {
		if !strings.HasPrefix(flow, "xtls-rprx-vision") {
			return nil, fmt.Errorf("vless %s: flow %s не поддерживается", name, flow)
		}
		ob["flow"] = "xtls-rprx-vision"
	}
	if tls := tlsOptions(q.Get("security"), q.Get("sni"), q.Get("fp"), q.Get("alpn"), q.Get("pbk"), q.Get("sid"), truthy(q.Get("allowInsecure")) || truthy(q.Get("insecure")), server); tls != nil {
		ob["tls"] = tls
	}
	tr, err := transportOptions(q.Get("type"), q.Get("path"), q.Get("host"), q.Get("serviceName"), q.Get("headerType"))
	if err != nil {
		return nil, fmt.Errorf("vless %s: %w", name, err)
	}
	if tr != nil {
		ob["transport"] = tr
	}
	return &Node{Name: name, Type: "vless", Server: server, Port: port, Outbound: ob}, nil
}

func parseTrojan(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("trojan: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("trojan: %w", err)
	}
	name := fragmentName(u)
	q := u.Query()
	password := u.User.Username()
	ob := map[string]any{
		"type":        "trojan",
		"server":      server,
		"server_port": port,
		"password":    password,
	}
	security := q.Get("security")
	if security == "" {
		security = "tls"
	}
	if tls := tlsOptions(security, firstNonEmpty(q.Get("sni"), q.Get("peer")), q.Get("fp"), q.Get("alpn"), q.Get("pbk"), q.Get("sid"), truthy(q.Get("allowInsecure")) || truthy(q.Get("insecure")), server); tls != nil {
		ob["tls"] = tls
	}
	tr, err := transportOptions(q.Get("type"), q.Get("path"), q.Get("host"), q.Get("serviceName"), q.Get("headerType"))
	if err != nil {
		return nil, fmt.Errorf("trojan %s: %w", name, err)
	}
	if tr != nil {
		ob["transport"] = tr
	}
	return &Node{Name: name, Type: "trojan", Server: server, Port: port, Outbound: ob}, nil
}

type vmessJSON struct {
	PS   string          `json:"ps"`
	Add  string          `json:"add"`
	Port json.RawMessage `json:"port"`
	ID   string          `json:"id"`
	Aid  json.RawMessage `json:"aid"`
	Scy  string          `json:"scy"`
	Net  string          `json:"net"`
	Type string          `json:"type"`
	Host string          `json:"host"`
	Path string          `json:"path"`
	TLS  string          `json:"tls"`
	SNI  string          `json:"sni"`
	ALPN string          `json:"alpn"`
	FP   string          `json:"fp"`
}

func rawInt(r json.RawMessage) int {
	s := strings.Trim(string(r), "\" ")
	n, _ := strconv.Atoi(s)
	return n
}

func parseVMess(link string) (*Node, error) {
	body := strings.TrimPrefix(link[strings.Index(link, "://")+3:], "")
	if i := strings.Index(body, "#"); i >= 0 {
		body = body[:i]
	}
	decoded, ok := decodeBase64(body)
	if !ok {
		return nil, errors.New("vmess: ссылка не в формате base64 JSON")
	}
	var v vmessJSON
	if err := json.Unmarshal([]byte(decoded), &v); err != nil {
		return nil, fmt.Errorf("vmess: %w", err)
	}
	port := rawInt(v.Port)
	if v.Add == "" || port <= 0 || v.ID == "" {
		return nil, fmt.Errorf("vmess %s: неполные данные", v.PS)
	}
	security := v.Scy
	if security == "" {
		security = "auto"
	}
	ob := map[string]any{
		"type":            "vmess",
		"server":          v.Add,
		"server_port":     port,
		"uuid":            v.ID,
		"security":        security,
		"alter_id":        rawInt(v.Aid),
		"packet_encoding": "xudp",
	}
	if tls := tlsOptions(v.TLS, v.SNI, v.FP, v.ALPN, "", "", false, v.Add); tls != nil {
		ob["tls"] = tls
	}
	tr, err := transportOptions(v.Net, v.Path, v.Host, v.Path, v.Type)
	if err != nil {
		return nil, fmt.Errorf("vmess %s: %w", v.PS, err)
	}
	if tr != nil {
		ob["transport"] = tr
	}
	return &Node{Name: v.PS, Type: "vmess", Server: v.Add, Port: port, Outbound: ob}, nil
}

func parseShadowsocks(link string) (*Node, error) {
	rest := link[len("ss://"):]
	name := ""
	if i := strings.Index(rest, "#"); i >= 0 {
		name, _ = url.PathUnescape(rest[i+1:])
		rest = rest[:i]
	}
	var query string
	if i := strings.Index(rest, "?"); i >= 0 {
		query = rest[i+1:]
		rest = rest[:i]
	}
	rest = strings.TrimSuffix(rest, "/")
	var userinfo, hostport string
	if i := strings.LastIndex(rest, "@"); i >= 0 {
		userinfo, hostport = rest[:i], rest[i+1:]
		if dec, ok := decodeBase64(userinfo); ok && strings.Contains(dec, ":") {
			userinfo = dec
		} else if un, err := url.PathUnescape(userinfo); err == nil {
			userinfo = un
		}
	} else {
		dec, ok := decodeBase64(rest)
		if !ok {
			return nil, errors.New("ss: не удалось разобрать ссылку")
		}
		i := strings.LastIndex(dec, "@")
		if i < 0 {
			return nil, errors.New("ss: не удалось разобрать ссылку")
		}
		userinfo, hostport = dec[:i], dec[i+1:]
	}
	method, password, ok := strings.Cut(userinfo, ":")
	if !ok {
		return nil, fmt.Errorf("ss %s: нет метода/пароля", name)
	}
	host, portStr, err := net.SplitHostPort(hostport)
	port, perr := strconv.Atoi(portStr)
	if err != nil || perr != nil || port <= 0 {
		return nil, fmt.Errorf("ss %s: неверный адрес", name)
	}
	ob := map[string]any{
		"type":        "shadowsocks",
		"server":      host,
		"server_port": port,
		"method":      method,
		"password":    password,
	}
	if q, _ := url.ParseQuery(query); q.Get("plugin") != "" {
		plugin, opts, err := ssPlugin(q.Get("plugin"))
		if err != nil {
			return nil, fmt.Errorf("ss %s: %w", name, err)
		}
		ob["plugin"] = plugin
		if opts != "" {
			ob["plugin_opts"] = opts
		}
	}
	return &Node{Name: strings.TrimSpace(name), Type: "shadowsocks", Server: host, Port: port, Outbound: ob}, nil
}

func parseHysteria2(link string) (*Node, error) {
	if strings.HasPrefix(strings.ToLower(link), "hy2://") {
		link = "hysteria2://" + link[len("hy2://"):]
	}
	link, portSpec := splitMultiPort(link)
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("hysteria2: %w", err)
	}
	q := u.Query()
	name := fragmentName(u)
	server := u.Hostname()
	port, err := strconv.Atoi(u.Port())
	if err != nil || port <= 0 {
		return nil, fmt.Errorf("hysteria2 %s: неверный порт", name)
	}
	ob := map[string]any{"type": "hysteria2", "server": server}
	if portSpec == "" {
		portSpec = q.Get("mport")
	}
	applyPorts(ob, port, portSpec)
	if server == "" {
		return nil, fmt.Errorf("hysteria2 %s: нет адреса", name)
	}
	password := u.User.Username()
	if p, ok := u.User.Password(); ok {
		password = password + ":" + p
	}
	if password == "" {
		password = q.Get("auth")
	}
	ob["password"] = password
	if obfs := q.Get("obfs"); obfs != "" && obfs != "none" {
		ob["obfs"] = map[string]any{"type": obfs, "password": q.Get("obfs-password")}
	}
	ob["tls"] = tlsOptions("tls", q.Get("sni"), "", firstNonEmpty(q.Get("alpn"), "h3"), "", "", truthy(q.Get("insecure")), server)
	return &Node{Name: name, Type: "hysteria2", Server: server, Port: port, Outbound: ob}, nil
}

func parseTUIC(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("tuic: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("tuic: %w", err)
	}
	q := u.Query()
	name := fragmentName(u)
	password, _ := u.User.Password()
	ob := map[string]any{
		"type":        "tuic",
		"server":      server,
		"server_port": port,
		"uuid":        u.User.Username(),
		"password":    password,
	}
	if cc := q.Get("congestion_control"); cc != "" {
		ob["congestion_control"] = cc
	}
	if m := q.Get("udp_relay_mode"); m != "" {
		ob["udp_relay_mode"] = m
	}
	ob["tls"] = tlsOptions("tls", q.Get("sni"), "", firstNonEmpty(q.Get("alpn"), "h3"), "", "", truthy(q.Get("allow_insecure")) || truthy(q.Get("insecure")), server)
	return &Node{Name: name, Type: "tuic", Server: server, Port: port, Outbound: ob}, nil
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

// wireguard://PRIVATE_KEY@host:port?publickey=…&address=10.0.0.2/32,fd00::2/128&presharedkey=…&reserved=1,2,3&mtu=1280#name
func parseWireGuard(link string) (*Node, error) {
	if strings.HasPrefix(strings.ToLower(link), "wg://") {
		link = "wireguard://" + link[len("wg://"):]
	}
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("wireguard: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("wireguard: %w", err)
	}
	q := u.Query()
	name := fragmentName(u)
	privateKey, _ := url.PathUnescape(u.User.Username())
	if privateKey == "" {
		privateKey = q.Get("privatekey")
	}
	publicKey := firstNonEmpty(q.Get("publickey"), q.Get("peer_public_key"), q.Get("pbk"))
	if privateKey == "" || publicKey == "" {
		return nil, fmt.Errorf("wireguard %s: нет ключей", name)
	}
	var addrs []string
	for _, a := range splitList(firstNonEmpty(q.Get("address"), q.Get("ip"))) {
		if !strings.Contains(a, "/") {
			if strings.Contains(a, ":") {
				a += "/128"
			} else {
				a += "/32"
			}
		}
		addrs = append(addrs, a)
	}
	if len(addrs) == 0 {
		return nil, fmt.Errorf("wireguard %s: нет адреса интерфейса", name)
	}
	peer := map[string]any{
		"address":     server,
		"port":        port,
		"public_key":  publicKey,
		"allowed_ips": []string{"0.0.0.0/0", "::/0"},
	}
	if psk := firstNonEmpty(q.Get("presharedkey"), q.Get("psk")); psk != "" {
		peer["pre_shared_key"] = psk
	}
	if r := splitList(q.Get("reserved")); len(r) == 3 {
		var res []int
		for _, v := range r {
			n, err := strconv.Atoi(v)
			if err != nil || n < 0 || n > 255 {
				res = nil
				break
			}
			res = append(res, n)
		}
		if res != nil {
			peer["reserved"] = res
		}
	}
	ob := map[string]any{
		"type":        "wireguard",
		"address":     addrs,
		"private_key": privateKey,
		"peers":       []any{peer},
	}
	if mtu, err := strconv.Atoi(q.Get("mtu")); err == nil && mtu > 0 {
		ob["mtu"] = mtu
	}
	return &Node{Name: name, Type: "wireguard", Server: server, Port: port, Outbound: ob, Endpoint: true}, nil
}
