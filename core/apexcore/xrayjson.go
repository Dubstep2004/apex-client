package apexcore

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Подписка в формате Xray JSON (как её получает Happ): массив полных конфигов Xray
// или один конфиг. Из каждого берём исходящий «proxy» (или первый прокси) — он и есть
// сервер; название — remarks, подпись — meta.serverDescription. Такие серверы
// подключаются через Xray как есть, со всеми настройками XHTTP (extra и т. п.).
// Ступень «dialerProxy» (например, freedom с фрагментацией) сохраняется в xrayChainKey.

const xrayChainKey = "_apexChain"

var xrayProxyProtocols = map[string]bool{
	"vless": true, "vmess": true, "trojan": true, "shadowsocks": true, "hysteria": true,
}

// isXrayJSON — JSON похож на конфиг(и) Xray: исходящие с полем protocol, а не type.
func isXrayJSON(content string) bool {
	s := strings.TrimSpace(content)
	if strings.HasPrefix(s, "[") {
		return true
	}
	var cfg struct {
		Outbounds []map[string]any `json:"outbounds"`
	}
	if json.Unmarshal([]byte(s), &cfg) != nil {
		return false
	}
	for _, ob := range cfg.Outbounds {
		if _, ok := ob["protocol"]; ok {
			return true
		}
	}
	return false
}

func parseXrayJSON(content string, res *parseResult) error {
	s := strings.TrimSpace(content)
	var configs []map[string]any
	if strings.HasPrefix(s, "[") {
		if err := json.Unmarshal([]byte(s), &configs); err != nil {
			return fmt.Errorf("неверный JSON подписки Xray: %w", err)
		}
	} else {
		var one map[string]any
		if err := json.Unmarshal([]byte(s), &one); err != nil {
			return fmt.Errorf("неверный JSON подписки Xray: %w", err)
		}
		configs = []map[string]any{one}
	}
	for i, cfg := range configs {
		name, _ := cfg["remarks"].(string)
		if strings.TrimSpace(name) == "" {
			name = fmt.Sprintf("Сервер %d", i+1)
		}
		n, err := xrayJSONNode(cfg, name)
		if err != nil {
			res.Skipped = append(res.Skipped, fmt.Sprintf("%s: %v", name, err))
			continue
		}
		if n != nil {
			res.Nodes = append(res.Nodes, *n)
		}
	}
	return nil
}

func xrayJSONNode(cfg map[string]any, name string) (*Node, error) {
	list, _ := cfg["outbounds"].([]any)
	byTag := map[string]map[string]any{}
	var proxy map[string]any
	for _, v := range list {
		ob, ok := v.(map[string]any)
		if !ok {
			continue
		}
		tag, _ := ob["tag"].(string)
		if tag != "" {
			byTag[tag] = ob
		}
		proto, _ := ob["protocol"].(string)
		if !xrayProxyProtocols[proto] {
			continue
		}
		if proxy == nil || tag == "proxy" {
			proxy = ob
		}
	}
	if proxy == nil {
		return nil, nil // в конфиге нет сервера (только direct/block)
	}
	ob := deepCopy(proxy).(map[string]any)
	delete(ob, "tag")
	delete(ob, "mux") // мультиплексирование включает само приложение
	proto, _ := ob["protocol"].(string)
	server, port := xrayServerPort(ob)
	if server == "" || port <= 0 {
		return nil, fmt.Errorf("%s: нет адреса сервера", proto)
	}
	// Ступень перед сервером (dialerProxy): берём её с собой, одну
	if stream, _ := ob["streamSettings"].(map[string]any); stream != nil {
		if so, _ := stream["sockopt"].(map[string]any); so != nil {
			if dp, _ := so["dialerProxy"].(string); dp != "" {
				hop, ok := byTag[dp]
				if !ok {
					return nil, fmt.Errorf("нет ступени %s", dp)
				}
				if p, _ := hop["protocol"].(string); p != "freedom" {
					return nil, fmt.Errorf("цепочка через %s не поддерживается", p)
				}
				h := deepCopy(hop).(map[string]any)
				delete(h, "tag")
				if hs, _ := h["streamSettings"].(map[string]any); hs != nil {
					if hso, _ := hs["sockopt"].(map[string]any); hso != nil {
						delete(hso, "dialerProxy")
					}
				}
				ob[xrayChainKey] = []any{h}
				delete(so, "dialerProxy")
			}
		}
	}
	n := &Node{Name: strings.TrimSpace(name), Type: proto, Server: server, Port: port, Xray: ob}
	if st, _ := ob["settings"].(map[string]any); proto == "hysteria" && st != nil && anyInt(st["version"]) == 2 {
		n.Category = "Hysteria 2" // в Xray Hysteria 2 — это hysteria с version 2
	}
	if m, ok := cfg["meta"].(map[string]any); ok {
		n.Description, _ = m["serverDescription"].(string)
	}
	return n, nil
}

// xrayServerPort — адрес и порт сервера из settings (vnext / servers / hysteria).
func xrayServerPort(ob map[string]any) (string, int) {
	st, _ := ob["settings"].(map[string]any)
	if st == nil {
		return "", 0
	}
	for _, key := range []string{"vnext", "servers"} {
		if arr, _ := st[key].([]any); len(arr) > 0 {
			if m, _ := arr[0].(map[string]any); m != nil {
				a, _ := m["address"].(string)
				return a, anyInt(m["port"])
			}
		}
	}
	a, _ := st["address"].(string)
	return a, anyInt(st["port"])
}

func anyInt(v any) int {
	switch t := v.(type) {
	case float64:
		return int(t)
	case int:
		return t
	case string:
		n := 0
		fmt.Sscanf(strings.TrimSpace(t), "%d", &n)
		return n
	}
	return 0
}
