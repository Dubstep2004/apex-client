// apexcli — мост между приложением APEX VPN для Windows и общим ядром apexcore
// (тот же разбор подписки и сборка конфигов sing-box/Xray, что и на Android).
//
// Запрос — JSON в stdin, ответ — JSON в stdout: {"ok":true,"result":…} или {"ok":false,"error":"…"}.
//
//	{"cmd":"parse","content":"<тело подписки>"}
//	{"cmd":"build","nodes":"<JSON из parse>","selected":"<tag>","options":"<JSON настроек>"}
//	{"cmd":"udpping","nodes":"<JSON>","timeout":3000}
//	{"cmd":"warpkey"} / {"cmd":"warpparse","content":"<ответ Cloudflare>","key":"<закрытый ключ>"}
//	{"cmd":"version"}
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"

	"github.com/sagernet/sing-box/experimental/apexcore"
)

type request struct {
	Cmd      string `json:"cmd"`
	Content  string `json:"content"`
	Nodes    string `json:"nodes"`
	Selected string `json:"selected"`
	Options  string `json:"options"`
	Timeout  int32  `json:"timeout"`
	Key      string `json:"key"`
}

func reply(result string, err error) {
	out := map[string]any{"ok": err == nil}
	if err != nil {
		out["error"] = err.Error()
	} else {
		out["result"] = result
	}
	_ = json.NewEncoder(os.Stdout).Encode(out)
}

func main() {
	data, err := io.ReadAll(os.Stdin)
	if err != nil {
		reply("", err)
		return
	}
	var req request
	if err := json.Unmarshal(data, &req); err != nil {
		reply("", fmt.Errorf("неверный запрос: %w", err))
		return
	}
	switch req.Cmd {
	case "parse":
		reply(apexcore.ParseSubscription(req.Content))
	case "build":
		reply(apexcore.BuildConfigs(req.Nodes, req.Selected, req.Options))
	case "udpping":
		reply(apexcore.UdpPing(req.Nodes, req.Timeout))
	case "warpkey":
		reply(apexcore.WarpKeypair())
	case "warpparse":
		reply(apexcore.WarpParse(req.Content, req.Key))
	case "version":
		reply("apexcli 1", nil)
	default:
		reply("", fmt.Errorf("неизвестная команда %q", req.Cmd))
	}
}
