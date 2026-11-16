package apexcore

import (
	"encoding/base64"
	"regexp"
	"strings"
	"unicode/utf8"
)

// Заголовки подписки, которые можно передать и в теле строкой «#имя: значение» (как в Happ).
var metaKeys = map[string]bool{
	"profile-title": true, "announce": true, "support-url": true, "profile-web-page-url": true,
	"profile-update-interval": true, "subscription-userinfo": true,
	"announce-image": true, "announce-button": true, "announce-url": true,
}

var metaRe = regexp.MustCompile(`^#\s*([A-Za-z-]+)\s*:\s*(.*)$`)

func metaLine(line string) (string, string, bool) {
	m := metaRe.FindStringSubmatch(line)
	if m == nil {
		return "", "", false
	}
	key := strings.ToLower(m[1])
	if !metaKeys[key] {
		return "", "", false
	}
	return key, strings.TrimSpace(m[2]), true
}

// splitDescription отделяет «#Имя?serverDescription=<base64>» — формат подписи Happ.
func splitDescription(n *Node) {
	const marker = "?serverDescription="
	if i := strings.Index(n.Name, marker); i >= 0 {
		raw := strings.TrimSpace(n.Name[i+len(marker):])
		n.Name = strings.TrimSpace(n.Name[:i])
		if j := strings.IndexAny(raw, "&?"); j >= 0 {
			raw = raw[:j]
		}
		n.Description = decodeDescription(raw)
	}
	n.Description = strings.TrimSpace(n.Description)
	// Пометка о рекламе — прежде всего в описании (как «⚠️ РЕКЛАМА НА YOUTUBE ЕСТЬ!»).
	// «Ютуб без рекламы», «no ads» — наоборот, сервер без рекламы.
	n.Ads = mentionsAds(n.Description) || mentionsAds(n.Name)
	// 3.22: серверы для нейросетей («Доступ к нейросетям», «ChatGPT», «AI») — автопилот их не выбирает
	n.AI = mentionsAI(n.Description) || mentionsAI(n.Name)
}

var (
	adsRe   = regexp.MustCompile(`(?i)реклам|\bads?\b|advert`)
	noAdsRe = regexp.MustCompile(`(?i)(без|нет|no|without|free)\s*(\p{L}+\s+)?(реклам|ads?\b|advert)|(реклам\p{L}*|ads?)[\s:\-—]*(нет|free)`)
)

// В Go \b понимает только латиницу — для «ИИ» и «AI» границы слова записаны явно.
var aiRe = regexp.MustCompile(`(?i)нейросет|нейронк|нейро-?сет|chat\s?gpt|openai|gemini|claude|deepseek|perplexity|copilot|midjourney|(^|[^\p{L}])(ии|ai|gpt|grok)([^\p{L}]|$)`)

func mentionsAI(s string) bool { return aiRe.MatchString(s) }

func mentionsAds(s string) bool {
	return adsRe.MatchString(s) && !noAdsRe.MatchString(s)
}

func decodeDescription(raw string) string {
	for _, enc := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
		if b, err := enc.DecodeString(raw); err == nil && isText(b) {
			return string(b)
		}
	}
	return raw
}

func isText(b []byte) bool {
	s := string(b)
	if utf8.ValidString(s) && len(s) > 0 {
		for _, r := range s {
			if r < 0x20 && r != '\n' && r != '\t' {
				return false
			}
		}
		return true
	}
	return false
}
