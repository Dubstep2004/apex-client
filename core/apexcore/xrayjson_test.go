package apexcore

import (
	"encoding/json"
	"strings"
	"testing"
)

const xrayJSONSub = `[{"remarks":"🇩🇪 Германия • 📶 LTE • 🛡 Обход • №1","meta":{"serverDescription":"📶 LTE Лимит: 150 ГБ в месяц"},
"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"cdn.example.com","port":443,"users":[{"id":"00000000-0000-4000-8000-000000000000","encryption":"none","flow":""}]}]},
"streamSettings":{"network":"xhttp","xhttpSettings":{"mode":"packet-up","host":"cdn.example.com","path":"/video/download","extra":{"seqKey":"chunk","uplinkHTTPMethod":"HEAD"}},
"security":"tls","tlsSettings":{"serverName":"cdn.example.com","fingerprint":"firefox","alpn":["h2"]}}},
{"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}]},
{"remarks":"Фрагмент","outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"1.2.3.4","port":8443,"password":"x"}]},
"streamSettings":{"network":"tcp","security":"tls","sockopt":{"dialerProxy":"fragment"}}},
{"tag":"fragment","protocol":"freedom","settings":{"fragment":{"packets":"tlshello","length":"10-20","interval":"10-20"}}}]},
{"remarks":"Пусто","outbounds":[{"tag":"direct","protocol":"freedom"}]}]`

func TestXrayJSONSubscription(t *testing.T) {
	res, err := parse(xrayJSONSub)
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Nodes) != 2 {
		t.Fatalf("nodes=%d", len(res.Nodes))
	}
	n := res.Nodes[0]
	if n.Server != "cdn.example.com" || n.Port != 443 || n.Type != "vless" || n.Outbound != nil || n.Xray == nil {
		t.Fatalf("node=%+v", n)
	}
	if !strings.Contains(n.Description, "150") || !strings.Contains(n.Name, "Германия") {
		t.Fatalf("name=%q desc=%q", n.Name, n.Description)
	}
	if !strings.Contains(n.Detail, "XHTTP") {
		t.Fatalf("detail=%q", n.Detail)
	}
	b, _ := json.Marshal(n.Xray)
	if !strings.Contains(string(b), "uplinkHTTPMethod") {
		t.Fatal("extra потерян")
	}
	// Конфиг Xray: XHTTP extra на месте, ступень фрагментации — перед сервером
	nodes, _ := json.Marshal(res.Nodes)
	out, err := BuildConfigs(string(nodes), AutoTag, `{"core":"xray"}`)
	if err != nil {
		t.Fatal(err)
	}
	var both map[string]string
	_ = json.Unmarshal([]byte(out), &both)
	xr := both["xray"]
	for _, want := range []string{"uplinkHTTPMethod", `"out-1-hop"`, `"tlshello"`, xrayDialTag} {
		if !strings.Contains(xr, want) {
			t.Fatalf("в конфиге Xray нет %s:\n%s", want, xr)
		}
	}
	if strings.Contains(xr, xrayChainKey) {
		t.Fatal("служебный ключ попал в конфиг")
	}
}
