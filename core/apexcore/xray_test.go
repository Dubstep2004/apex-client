package apexcore

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/experimental/libbox"
)

// настоящий формат ключа VLESS Encryption: …0rtt.<32 байта в base64url>
var testEnc = "mlkem768x25519plus.native.0rtt." + base64.RawURLEncoding.EncodeToString([]byte("0123456789abcdef0123456789abcdef"))

func xrayLinks() []string {
	vmessXHTTP := base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"VMess XHTTP","add":"vx.example.com","port":"443","id":"` + testUUID + `","aid":"0","scy":"auto","net":"xhttp","type":"packet-up","host":"vx.example.com","path":"/x","tls":"tls","sni":"vx.example.com"}`))
	return []string{
		"vless://" + testUUID + "@xh.example.com:443?type=xhttp&mode=stream-one&path=%2Fxh&host=cdn.example.com&security=tls&sni=xh.example.com&fp=chrome&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D#XHTTP%20TLS",
		"vless://" + testUUID + "@5.5.5.5:443?type=xhttp&security=reality&pbk=" + testPBK + "&sni=www.apple.com&sid=ab&spx=%2F#XHTTP%20Reality",
		"vless://" + testUUID + "@6.6.6.6:4000?type=kcp&headerType=wechat-video&seed=s3cr3t&security=tls&sni=kcp.example.com#mKCP",
		"vless://" + testUUID + "@7.7.7.7:443?encryption=" + testEnc + "&type=tcp&security=none&flow=xtls-rprx-vision#Encryption",
		"vmess://" + vmessXHTTP,
		// обычные — работают в sing-box, но знают и Xray
		"vless://" + testUUID + "@1.2.3.4:443?type=tcp&security=reality&pbk=" + testPBK + "&sni=www.microsoft.com&sid=6b&flow=xtls-rprx-vision#Reality",
		"trojan://pw@tr.example.com:443?type=ws&path=%2Fws#Trojan%20WS",
		"hysteria2://pw@hy.example.com:443?sni=hy.example.com#Hy2",
	}
}

type twoConfigs struct {
	Singbox string `json:"singbox"`
	Xray    string `json:"xray"`
}

func buildBoth(t *testing.T, nodes []Node, opts string) twoConfigs {
	t.Helper()
	data, _ := json.Marshal(nodes)
	out, err := BuildConfigs(string(data), AutoTag, opts)
	if err != nil {
		t.Fatal(err)
	}
	var c twoConfigs
	json.Unmarshal([]byte(out), &c)
	if err := libbox.CheckConfig(c.Singbox); err != nil {
		t.Fatalf("sing-box rejected: %v\n%s", err, c.Singbox)
	}
	return c
}

func TestXrayNodes(t *testing.T) {
	lookupIPs = func(host string) []string { return []string{"203.0.113.7"} }
	nodes := parseNodes(t, strings.Join(xrayLinks(), "\n"))
	if len(nodes) != 8 {
		t.Fatalf("want 8 nodes, got %d", len(nodes))
	}
	for i, n := range nodes {
		t.Logf("%-14s %-8s %-40s singbox=%v xray=%v udp=%v", n.Name, n.Category, n.Detail, n.Outbound != nil, n.Xray != nil, n.UDP)
		if i < 5 && n.Outbound != nil {
			t.Errorf("%s must be Xray-only", n.Name)
		}
		if i < 5 && !strings.HasSuffix(n.Detail, "Xray") {
			t.Errorf("%s: detail %q", n.Name, n.Detail)
		}
	}
	if !nodes[2].UDP {
		t.Error("mKCP is UDP")
	}
	if nodes[7].Xray != nil {
		t.Error("Hysteria2 must stay in sing-box")
	}

	// Обычный режим: через Xray только 5 «особых» серверов
	c := buildBoth(t, nodes, `{"xrayAuth":"k"}`)
	if strings.Count(c.Xray, `"protocol": "socks"`) != 6 {
		t.Fatalf("want 5 xray inbounds:\n%s", c.Xray)
	}
	for _, want := range []string{`"203.0.113.7/32"`, `"5.5.5.5/32"`, `"6.6.6.6/32"`} {
		if !strings.Contains(c.Singbox, want) {
			t.Fatalf("sing-box must route %s direct", want)
		}
	}
	if !strings.Contains(c.Xray, `"serverName": "xh.example.com"`) || !strings.Contains(c.Xray, `"xPaddingBytes"`) || !strings.Contains(c.Xray, `"mode": "stream-one"`) {
		t.Fatalf("xhttp settings lost:\n%s", c.Xray)
	}
	// Xray выходит в сеть через sing-box (вход apex-dial → direct), а не через туннель
	if strings.Count(c.Xray, `"dialerProxy": "apex-dial"`) != 5 || !strings.Contains(c.Singbox, `"tag": "apex-dial"`) {
		t.Fatalf("xray must dial via sing-box:\n%s", c.Xray)
	}
	if !strings.Contains(c.Xray, `"address": "203.0.113.7"`) {
		t.Fatal("domain must be pinned to IP")
	}
	if !strings.Contains(c.Xray, `"encryption": "`+testEnc+`"`) || !strings.Contains(c.Xray, `"value": "s3cr3t"`) || !strings.Contains(c.Xray, `"header": "wechat"`) {
		t.Fatalf("encryption/kcp finalmask lost:\n%s", c.Xray)
	}
	if strings.Contains(c.Xray, "allowInsecure") {
		t.Fatal("allowInsecure is removed in Xray 26")
	}
	// Тот же секрет — тот же конфиг (Xray не перезапускается без нужды)
	if c2 := buildBoth(t, nodes, `{"xrayAuth":"k"}`); c2.Xray != c.Xray {
		t.Fatal("xray config must be stable")
	}

	// Как в Happ: Xray для всех, кто его умеет (7 из 8)
	c = buildBoth(t, nodes, `{"xrayAll":true,"xrayAuth":"k"}`)
	if strings.Count(c.Xray, `"protocol": "socks"`) != 8 {
		t.Fatalf("want 7 xray inbounds")
	}
	if !strings.Contains(c.Singbox, `"type": "hysteria2"`) {
		t.Fatal("Hysteria2 stays in sing-box")
	}

	// Без Xray-узлов конфиг Xray пустой
	c = buildBoth(t, nodes[7:], "")
	if c.Xray != "" {
		t.Fatal("xray config must be empty")
	}
}

// TestXrayDump сохраняет конфиги Xray для проверки настоящим Xray в CI:
// APEX_XRAY_DUMP=dir go test -run XrayDump; затем xray run -test -c dir/*.json
func TestXrayDump(t *testing.T) {
	dir := os.Getenv("APEX_XRAY_DUMP")
	if dir == "" {
		t.Skip("APEX_XRAY_DUMP не задан")
	}
	lookupIPs = func(host string) []string { return []string{"203.0.113.7"} }
	ss := base64.RawURLEncoding.EncodeToString([]byte("2022-blake3-aes-128-gcm:8JCsPssfgS8tiRwiMlhARg=="))
	vmessKCP := base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"VMess KCP","add":"8.8.4.4","port":"5000","id":"` + testUUID + `","aid":"0","net":"kcp","type":"srtp","path":"seed1","tls":""}`))
	vmessGRPC := base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"VMess gRPC","add":"g.example.com","port":"443","id":"` + testUUID + `","aid":"0","net":"grpc","type":"multi","path":"svc","tls":"tls"}`))
	links := append(xrayLinks(),
		"vmess://"+vmessKCP,
		"vmess://"+vmessGRPC,
		"vless://"+testUUID+"@hu.example.com:443?type=httpupgrade&path=%2Fup&host=hu.example.com&security=tls#HU",
		"vless://"+testUUID+"@th.example.com:80?type=tcp&headerType=http&path=%2Fa&host=th.example.com#TCP%20HTTP",
		"vless://"+testUUID+"@k2.example.com:4000?security=tls&type=kcp&fm=%7B%22udp%22%3A%5B%7B%22type%22%3A%22mkcp-legacy%22%2C%22settings%22%3A%7B%22value%22%3A%22x%22%7D%7D%5D%7D#KCP%20fm",
		"vless://"+testUUID+"@k3.example.com:4000?security=tls&type=kcp&headerType=dns&host=dns.example.com#KCP%20dns",
		"vless://"+testUUID+"@192.168.1.5:4000?type=kcp&security=none#KCP%20LAN",
		"trojan://pw@tg.example.com:443?type=grpc&serviceName=t&security=tls&pcs=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855#Trojan%20pcs",
		"ss://"+ss+"@9.9.9.9:8388#SS2022",
	)
	nodes := parseNodes(t, strings.Join(links, "\n"))
	data, _ := json.Marshal(nodes)
	for i, opts := range []string{`{"xrayAuth":"k"}`, `{"xrayAll":true,"xrayAuth":"k"}`} {
		out, err := BuildConfigs(string(data), AutoTag, opts)
		if err != nil {
			t.Fatal(err)
		}
		var c twoConfigs
		json.Unmarshal([]byte(out), &c)
		if err := os.WriteFile(filepath.Join(dir, fmt.Sprintf("xray-%d.json", i)), []byte(c.Xray), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	// каждый сервер отдельно — чтобы ошибка указывала на конкретную ссылку
	for i := range nodes {
		one, _ := json.Marshal(nodes[i : i+1])
		out, err := BuildConfigs(string(one), AutoTag, `{"xrayAll":true,"xrayAuth":"k"}`)
		if err != nil {
			t.Fatal(err)
		}
		var c twoConfigs
		json.Unmarshal([]byte(out), &c)
		if c.Xray != "" {
			name := strings.NewReplacer(" ", "_", "/", "_").Replace(nodes[i].Name)
			os.WriteFile(filepath.Join(dir, fmt.Sprintf("node-%02d-%s.json", i+1, name)), []byte(c.Xray), 0o644)
		}
	}
}

func TestXrayRefusesPlainVLESS(t *testing.T) {
	// VLESS без TLS: sing-box подключит, Xray 26 — нет; XHTTP без TLS не подключит никто
	nodes := parseNodes(t, "vless://"+testUUID+"@8.8.8.8:80?type=ws&security=none#Plain")
	if nodes[0].Xray != nil || nodes[0].Outbound == nil {
		t.Fatal("plain VLESS must stay in sing-box only")
	}
	_, err := ParseSubscription("vless://" + testUUID + "@8.8.8.8:80?type=xhttp&security=none#X")
	if err == nil || !strings.Contains(err.Error(), "без TLS") {
		t.Fatalf("want explanation, got %v", err)
	}
}

func TestXrayPortAndIPv4First(t *testing.T) {
	saved := lookupIPs
	defer func() { lookupIPs = saved }()
	calls := 0
	lookupIPs = func(string) []string { calls++; return []string{"203.0.113.9"} }
	nodes := parseNodes(t, strings.Join(xrayLinks()[:2], "\n")+"\nvless://"+testUUID+"@xh.example.com:443?type=xhttp&security=tls#Same%20host")
	data, _ := json.Marshal(nodes)
	_, xr, err := buildAll(string(data), AutoTag, `{"xrayPort":31000,"xrayAuth":"k"}`)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(xr, `"port": 31000`) || !strings.Contains(xr, `"port": 31001`) {
		t.Fatalf("custom xray port not used:\n%s", xr)
	}
	if calls != 1 {
		t.Fatalf("one domain must be resolved once, got %d", calls)
	}
	lookupIPs = saved
	if ips := lookupIPs("localhost"); len(ips) > 0 && strings.Contains(ips[0], ":") {
		t.Fatalf("IPv4 must come first: %v", ips)
	}
}
