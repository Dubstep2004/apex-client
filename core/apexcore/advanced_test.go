package apexcore

import (
	"encoding/json"
	"os"
	"strings"
	"testing"
)

func TestAdvancedRouting(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts := map[string]any{
		"mode":     "smart",
		"rulesDir": rulesDirForTest(),
		"userRules": []map[string]string{
			{"value": "example.org", "action": "proxy"},
			{"value": "keyword:casino", "action": "block"},
			{"value": "full:bank.ru", "action": "direct"},
			{"value": "10.20.0.0/16", "action": "direct"},
			{"value": "8.8.8.8", "action": "proxy"},
		},
		"domainStrategy": "ip_if_non_match",
		"fakeDns":        true,
		"remoteDns":      map[string]string{"type": "tls", "server": "dns.google", "ip": "8.8.8.8"},
		"directDns":      map[string]string{"type": "https", "server": "https://dns.yandex.ru/dns-query", "ip": "77.88.8.8"},
	}
	raw, _ := json.Marshal(opts)
	cfg := checkAll(t, nodes, string(raw))
	for _, want := range []string{`"fakeip"`, `"action": "resolve"`, `"8.8.8.8/32"`, `"server_name": "dns.google"`, `"tag": "home"`, `"casino"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("missing %s in config", want)
		}
	}
	opts["domainStrategy"] = "ip_on_demand"
	opts["remoteDns"] = map[string]string{"type": "udp", "server": "1.1.1.1"}
	raw, _ = json.Marshal(opts)
	checkAll(t, nodes, string(raw))

	for _, c := range []struct {
		typ, server, ip string
		ok              bool
	}{
		{"https", "https://cloudflare-dns.com/dns-query", "1.1.1.1", true},
		{"tls", "dns.google", "", true},
		{"quic", "dns.adguard-dns.com", "", true},
		{"udp", "8.8.8.8", "", true},
		{"https", "", "", false},
		{"weird", "1.1.1.1", "", false},
		{"https", "dns.google", "not-an-ip", false},
	} {
		if got := ValidateDNSSpec(c.typ, c.server, c.ip) == ""; got != c.ok {
			t.Errorf("%v: valid=%v", c, got)
		}
	}
	if ValidateUserRule("exa mple") == "" || ValidateUserRule("10.0.0.0/8") != "" || ValidateUserRule("keyword:x") != "" {
		t.Error("rule validation")
	}
	out, _ := MatchRoute(string(raw), "www.example.org")
	if !strings.Contains(out, `"proxy"`) || !strings.Contains(out, "Своё правило") {
		t.Fatalf("site check: %s", out)
	}
}

func TestPortRulesAndWarp(t *testing.T) {
	if e := ValidateUserRule("udp:27000-27100"); e != "" {
		t.Fatal(e)
	}
	if e := ValidateUserRule("port:70000"); e == "" {
		t.Fatal("порт 70000 должен быть ошибкой")
	}
	kp, err := WarpKeypair()
	if err != nil {
		t.Fatal(err)
	}
	priv, _, _ := strings.Cut(kp, " ")
	resp := `{"config":{"client_id":"AQID","peers":[{"public_key":"bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=","endpoint":{"v4":"162.159.192.7:0","host":"engage.cloudflareclient.com:2408"}}],"interface":{"addresses":{"v4":"172.16.0.2","v6":"2606:4700:110:8a36::1"}}}}`
	wj, err := WarpParse(resp, priv)
	if err != nil {
		t.Fatal(err)
	}
	var w WarpConfig
	_ = json.Unmarshal([]byte(wj), &w)
	if w.Host != "162.159.192.7" || w.Port != 2408 || len(w.Reserved) != 3 || w.Reserved[2] != 3 {
		t.Fatalf("WARP разобран неверно: %+v", w)
	}
	opts, _ := json.Marshal(map[string]any{
		"userRules": []map[string]string{{"value": "udp:27000-27100", "action": "direct"}, {"value": "tcp:25565", "action": "proxy"}},
		"warp":      w,
	})
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"27000:27100"`) || !strings.Contains(cfg, `"tag": "warp"`) || !strings.Contains(cfg, `"final": "warp"`) {
		t.Fatalf("нет правил по портам или WARP:\n%s", cfg)
	}
}

func TestAdsCounterDNS(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts, _ := json.Marshal(map[string]any{"adBlock": true, "rulesDir": rulesDirForTest(), "adsDnsPort": 45123})
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"adblock"`) || !strings.Contains(cfg, `45123`) {
		t.Fatalf("нет DNS счётчика рекламы:\n%s", cfg)
	}
}

func TestCustomLists(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts, _ := json.Marshal(map[string]any{
		"mode":        "selected",
		"services":    []string{"netflix", "figma"},
		"customLists": []map[string]any{{"name": "Работа", "items": []string{"jira.example.com", "10.8.0.0/16", "keyword:corp"}}},
	})
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"jira.example.com"`) || !strings.Contains(cfg, `"10.8.0.0/16"`) || !strings.Contains(cfg, `"nflxvideo.net"`) {
		t.Fatalf("нет своих списков или новых сервисов:\n%s", cfg)
	}
	out, _ := MatchRoute(string(opts), "jira.example.com")
	if !strings.Contains(out, "Работа") {
		t.Fatalf("проверка сайта: %s", out)
	}
}

func TestStableAuto(t *testing.T) {
	got := stableAuto([]string{"a", "ru", "b", "c"}, []string{"ru"}, []string{"c", "a"})
	if strings.Join(got, ",") != "c,a,b" {
		t.Fatalf("got %v", got)
	}
	if got := stableAuto([]string{"ru"}, []string{"ru"}, nil); len(got) != 1 {
		t.Fatalf("all excluded must keep original: %v", got)
	}
}

func TestAllDNSProviders(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	for id := range dnsServers {
		opts, _ := json.Marshal(map[string]any{"dns": id, "findProcess": true})
		cfg := checkAll(t, nodes, string(opts))
		if !strings.Contains(cfg, dnsServers[id]["server"].(string)) {
			t.Fatalf("%s: нет сервера DNS в конфиге", id)
		}
	}
}

func TestBlockQUIC(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	for _, on := range []bool{false, true} {
		raw, _ := json.Marshal(map[string]any{"mode": "global", "blockQuic": on})
		cfg := checkAll(t, nodes, string(raw))
		if got := strings.Contains(cfg, `"protocol": "quic"`); got != on {
			t.Fatalf("blockQuic=%v: правило QUIC есть=%v", on, got)
		}
	}
}

func TestProbeInbound(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	raw, _ := json.Marshal(map[string]any{"mode": "smart", "rulesDir": rulesDirForTest(), "probePort": 23007, "xrayAuth": "secret", "blockQuic": true})
	cfg := checkAll(t, nodes, string(raw))
	for _, want := range []string{`"probe-in"`, `"listen": "127.0.0.1"`, `"password": "secret"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("нет %s", want)
		}
	}
	// Правило замеров — раньше отказа QUIC, иначе DoQ не проверить
	if strings.Index(cfg, `"probe-in"`) > strings.Index(cfg, `"protocol": "quic"`) && strings.Count(cfg, `"probe-in"`) < 2 {
		t.Fatal("правило probe-in после QUIC")
	}
	raw, _ = json.Marshal(map[string]any{"mode": "smart", "rulesDir": rulesDirForTest()})
	if strings.Contains(checkAll(t, nodes, string(raw)), "probe-in") {
		t.Fatal("probe-in без порта")
	}
}

func TestProbePerNode(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A\nvless://"+testUUID+"@1.2.3.5:443?type=tcp&security=tls&sni=b.example.com#B")
	raw, _ := json.Marshal(map[string]any{"mode": "global", "probePort": 23007, "xrayAuth": "secret", "urlTest": "http://cp.cloudflare.com/"})
	cfg := checkAll(t, nodes, string(raw))
	for _, want := range []string{`"n:node-1"`, `"n:node-2"`, `"http://cp.cloudflare.com/"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("нет %s", want)
		}
	}
	if ProxyPing("node-1", "", "GET", "keepalive", 500) != -1 {
		t.Fatal("без ядра пинг через прокси должен быть -1")
	}
}

func TestDirectCountries(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	raw, _ := json.Marshal(map[string]any{"mode": "global", "directSuffixes": []string{"kz", ".by", "kaspi.kz", "bad suffix"}})
	cfg := checkAll(t, nodes, string(raw))
	for _, want := range []string{`"kz"`, `"by"`, `"kaspi.kz"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("нет %s", want)
		}
	}
	if strings.Contains(cfg, "bad suffix") {
		t.Fatal("мусор в правилах")
	}
}

func TestProxyInbound(t *testing.T) {
	nodes := `[{"tag":"a","name":"A","type":"vless","server":"1.2.3.4","port":443,"outbound":{"type":"vless","server":"1.2.3.4","server_port":443,"uuid":"` + "bf000d23-0752-40b4-affe-68f7707a9661" + `"}}]`
	cfg, err := BuildConfig(nodes, "a", `{"mode":"all","inbound":"proxy","proxyPort":30123}`)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(cfg, `"type": "tun"`) || !strings.Contains(cfg, `"listen_port": 30123`) || !strings.Contains(cfg, `"type": "mixed"`) {
		t.Fatalf("proxy inbound expected:\n%s", cfg)
	}
	cfg, _ = BuildConfig(nodes, "a", `{"mode":"all","directProcesses":["APEX VPN.exe"]}`)
	if !strings.Contains(cfg, `"process_name"`) || !strings.Contains(cfg, `"find_process": true`) {
		t.Fatalf("proxy inbound expected:\n%s", cfg)
	}
}

// Windows 2.3: раздача по дому и в режиме «Системный прокси» — вход share-in на 0.0.0.0
func TestShareWithProxyInbound(t *testing.T) {
	nodes := `[{"tag":"a","name":"A","type":"vless","server":"1.2.3.4","port":443,"outbound":{"type":"vless","server":"1.2.3.4","server_port":443,"uuid":"` + "bf000d23-0752-40b4-affe-68f7707a9661" + `"}}]`
	cfg, err := BuildConfig(nodes, "a", `{"mode":"smart","inbound":"proxy","proxyPort":30123,"share":{"enabled":true,"port":2080,"username":"apex","password":"secret12"}}`)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{`"tag": "proxy-in"`, `"tag": "share-in"`, `"listen": "0.0.0.0"`, `"listen_port": 2080`, `"password": "secret12"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("missing %s:\n%s", want, cfg)
		}
	}
	if os.Getenv("APEX_DUMP") != "" {
		_ = os.WriteFile(os.Getenv("APEX_DUMP"), []byte(cfg), 0o644)
	}
}
