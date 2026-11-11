package apexcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// AutoTag is the outbound that picks the fastest server automatically.
const AutoTag = "auto"

const (
	ModeSmart    = "smart"    // российское напрямую, остальное через VPN
	ModeAll      = "all"      // весь трафик через VPN
	ModeSelected = "selected" // через VPN только выбранные сервисы и свои сайты
)

const (
	fileGeoipRU   = "geoip-ru.srs"
	fileGeositeRU = "geosite-category-ru.srs"
	fileAds       = "geosite-category-ads-all.srs"
	fileAdsHagezi = "ads-hagezi.srs" // HaGeZi Multi Normal (необязательный: нет файла — без него)
	fileRuInside  = "ru-inside.srs"  // сайты, работающие только из России (runetfreedom, необязательный)
	fileThreats   = "threats-hagezi.srs"
	filePorn      = "geosite-category-porn.srs"
	fileBetting   = "geosite-category-betting-ru.srs"
)

// 15. Семейный режим: казино и букмекеры (дополнение к базе geosite-category-betting-ru)
var gamblingSuffixes = []string{
	"1xbet.com", "1xbet.ru", "1win.com", "1win.pro", "fonbet.ru", "fon.bet", "ligastavok.ru", "winline.ru",
	"marathonbet.ru", "marathonbet.com", "melbet.com", "melbet.ru", "mostbet.com", "parimatch.com", "parimatch.ru",
	"betcity.ru", "olimp.bet", "olimpbet.kz", "leon.ru", "leonbets.com", "pin-up.casino", "pinup.ru", "betboom.ru",
	"bet365.com", "betway.com", "unibet.com", "williamhill.com", "888casino.com", "888.com", "pokerstars.com",
	"ggpoker.com", "stake.com", "vavada.com", "joycasino.com", "azino777.ru", "casino-x.com", "baltbet.ru",
	"tennisi.bet", "zenit.win", "bingo-boom.ru", "pari.ru",
}
var gamblingKeywords = []string{"casino", "kazino", "azino", "vulkan-", "slots-"}

type shareOptions struct {
	Enabled  bool   `json:"enabled"`
	Port     int    `json:"port"`
	Username string `json:"username"`
	Password string `json:"password"`
}

type routeOptions struct {
	Mode          string       `json:"mode"`
	Services      []string     `json:"services"`      // включённые сервисы (через VPN)
	CustomDomains []string     `json:"customDomains"` // свои сайты — всегда через VPN
	DirectDomains []string     `json:"directDomains"` // свои сайты — всегда напрямую
	RulesDir      string       `json:"rulesDir"`      // папка с базами .srs
	DNS           string       `json:"dns"`           // cloudflare | google | quad9 | adguard | custom
	CustomDNS     string       `json:"customDns"`     // https://host/dns-query
	Kids          bool         `json:"kids"`          // семейный DNS для всех запросов
	AdBlock       bool         `json:"adBlock"`       // блокировка рекламы и трекеров
	BlockThreats  bool         `json:"blockThreats"`  // мошенники и вирусы (HaGeZi TIF, необязательная база)
	IPv6          bool         `json:"ipv6"`
	Profile       string       `json:"profile"` // normal | gaming | calls | battery
	Share         shareOptions `json:"share"`   // раздача на компьютер
	// Протоколы, среди которых автопилот выбирает сервер (категории Node.Category). Пусто — все.
	AutoProtocols []string `json:"autoProtocols"`
	// XrayAll — подключать через Xray все серверы, которые он умеет (как в Happ)
	XrayAll bool `json:"xrayAll"`
	// XrayAuth — постоянный секрет для локального SOCKS между sing-box и Xray
	XrayAuth string `json:"xrayAuth"`
	// Core — какое ядро подключает серверы: auto (sing-box, Xray — где нужно),
	// xray (всё, что умеет Xray) или singbox (без Xray)
	Core string `json:"core"`
	// XrayPort — первый локальный порт SOCKS для Xray (0 — XrayBasePort)
	XrayPort int `json:"xrayPort"`
	// ExcludeAds — автопилот не выбирает серверы с пометкой о рекламе
	ExcludeAds bool `json:"excludeAds"`
	// ClashAPI — порт локального API для замера задержки (0 — выключено)
	ClashAPI int `json:"clashApi"`

	// ── Для опытных ──
	UserRules      []UserRule `json:"userRules"`      // свои правила: домен/IP → VPN, напрямую, блок
	DomainStrategy string     `json:"domainStrategy"` // as_is | ip_if_non_match | ip_on_demand
	FakeDNS        bool       `json:"fakeDns"`        // поддельные IP вместо настоящих для сайтов через VPN
	RemoteDNS      *DNSSpec   `json:"remoteDns"`      // удалённый DNS (через VPN), вместо выбора из списка
	DirectDNS      *DNSSpec   `json:"directDns"`      // домашний DNS (напрямую), вместо системного
	// 31. WARP-цепочка: сайты через VPN выходят в интернет с IP Cloudflare
	Warp *WarpConfig `json:"warp"`
	// 24. LogFile — журнал ядра для отчёта в поддержку (только предупреждения и ошибки)
	LogFile string `json:"logFile"`
	// Свои списки «Через VPN» (создаёт пользователь): название и домены/IP, как в своих правилах
	CustomLists []CustomList `json:"customLists"`
	// 197. AdsDNSPort — локальный DNS приложения, который отвечает «нет такого адреса»
	// рекламным доменам и считает блокировки (0 — просто отказ в ядре)
	AdsDNSPort int `json:"adsDnsPort"`
	// 413. Определять приложение для каждого соединения (трафик по приложениям)
	FindProcess bool `json:"findProcess"`
	// Стабильный автопилот: какие серверы не брать (Россия, обход LTE) и в каком
	// порядке пробовать до первой проверки (по последнему замеру пинга в приложении)
	AutoExclude []string `json:"autoExclude"`
	AutoOrder   []string `json:"autoOrder"`
	// Без QUIC (HTTP/3): браузер и приложения сразу идут по TCP. На мобильной сети UDP часто
	// режут или теряют, и QUIC через VPN заметно тормозит — по TCP сайты открываются быстрее
	BlockQUIC bool `json:"blockQuic"`
	// ProbePort — локальный SOCKS (с паролем XrayAuth) для замеров DNS через сервер.
	// Всё, что пришло в него, идёт только через VPN, мимо остальных правил
	ProbePort int `json:"probePort"`
	// URLTest — адрес проверки для автопилота (как «Настройки URL-теста» в приложении)
	URLTest string `json:"urlTest"`
	// 602. Сайты стран напрямую: домены верхнего уровня («kz», «by») и важные сайты стран
	DirectSuffixes []string `json:"directSuffixes"`
	// Windows: «tun» (по умолчанию, нужны права администратора) или «proxy» — без TUN,
	// только локальный HTTP/SOCKS-прокси 127.0.0.1:ProxyPort (системный прокси ставит приложение)
	Inbound   string `json:"inbound"`
	ProxyPort int    `json:"proxyPort"`
	// DirectProcesses — программы, чьи соединения идут мимо VPN (Windows: само приложение,
	// чтобы пинг до серверов и проверка обновлений шли напрямую и в режиме TUN)
	DirectProcesses []string `json:"directProcesses"`
}

// профиль подключения: как часто проверять серверы и рвать ли соединения при смене
type profile struct {
	interval  string
	tolerance int
	interrupt bool
}

var profiles = map[string]profile{
	// Стабильный автопилот: сервер меняется, только если другой быстрее на 150 мс,
	// и смена не рвёт открытые соединения (видео и загрузки не обрываются)
	"normal":  {"2m", 150, false},
	"gaming":  {"1m", 40, false},   // минимальный пинг, игры не обрываются
	"calls":   {"10m", 300, false}, // сервер «залипает», звонки не рвутся
	"battery": {"30m", 200, false}, // редкие проверки — меньше расход батареи
}

var dnsServers = map[string]map[string]any{
	"cloudflare": {"type": "https", "server": "1.1.1.1"},
	"google":     {"type": "https", "server": "8.8.8.8"},
	"quad9":      {"type": "https", "server": "9.9.9.9"},
	"adguard":    {"type": "https", "server": "dns.adguard-dns.com", "domain_resolver": "local"},
	// Ещё DNS (2.3.1)
	"mullvad":  {"type": "https", "server": "dns.mullvad.net", "domain_resolver": "local"},
	"opendns":  {"type": "https", "server": "doh.opendns.com", "domain_resolver": "local"},
	"controld": {"type": "https", "server": "freedns.controld.com", "path": "/p0", "domain_resolver": "local"},
	"nextdns":  {"type": "https", "server": "dns.nextdns.io", "path": "/", "domain_resolver": "local"},
	"yandex":   {"type": "https", "server": "common.dot.dns.yandex.net", "domain_resolver": "local"},
	"comss":    {"type": "https", "server": "dns.comss.one", "domain_resolver": "local"},
}

var familyDNS = map[string]any{"type": "https", "server": "family.adguard-dns.com", "domain_resolver": "local"}

func parseOptions(optionsJSON string) (routeOptions, error) {
	opts := routeOptions{Mode: ModeSmart, DNS: "cloudflare", Profile: "normal"}
	if strings.TrimSpace(optionsJSON) != "" {
		if err := json.Unmarshal([]byte(optionsJSON), &opts); err != nil {
			return opts, fmt.Errorf("неверные настройки: %w", err)
		}
	}
	switch opts.Mode {
	case ModeSmart, ModeAll, ModeSelected:
	default:
		opts.Mode = ModeSmart
	}
	if _, ok := profiles[opts.Profile]; !ok {
		opts.Profile = "normal"
	}
	return opts, nil
}

func (o routeOptions) rulesFile(name string) string {
	if o.RulesDir == "" {
		return ""
	}
	p := filepath.Join(o.RulesDir, name)
	if _, err := os.Stat(p); err != nil {
		return ""
	}
	return p
}

// BuildConfig собирает полный конфиг sing-box.
//
//	nodesJSON   — массив "nodes" из ParseSubscription
//	selectedTag — "auto" или тег сервера
//	optionsJSON — настройки (см. routeOptions), можно ""
func BuildConfig(nodesJSON string, selectedTag string, optionsJSON string) (string, error) {
	sb, _, err := buildAll(nodesJSON, selectedTag, optionsJSON)
	return sb, err
}

// BuildConfigs — то же, что BuildConfig, плюс конфиг второго ядра Xray.
// Возвращает JSON {"singbox":"…","xray":"…"}; xray пустой, если Xray не нужен.
func BuildConfigs(nodesJSON string, selectedTag string, optionsJSON string) (string, error) {
	sb, xr, err := buildAll(nodesJSON, selectedTag, optionsJSON)
	if err != nil {
		return "", err
	}
	out, err := json.Marshal(map[string]string{"singbox": sb, "xray": xr})
	return string(out), err
}

func buildAll(nodesJSON string, selectedTag string, optionsJSON string) (string, string, error) {
	var nodes []Node
	if err := json.Unmarshal([]byte(nodesJSON), &nodes); err != nil {
		return "", "", fmt.Errorf("неверный список серверов: %w", err)
	}
	opts, err := parseOptions(optionsJSON)
	if err != nil {
		return "", "", err
	}
	xray := newXrayPlan(opts.XrayAuth)
	if opts.XrayPort > 1024 && opts.XrayPort < 65000 {
		xray.basePort = opts.XrayPort
	}
	xray.dialPort = xray.basePort - 1
	prof := profiles[opts.Profile]

	tags := make([]string, 0, len(nodes))
	found := selectedTag == AutoTag
	allowed := map[string]bool{}
	for _, c := range opts.AutoProtocols {
		allowed[c] = true
	}
	var autoTags []string
	var proxyOutbounds, endpoints []any
	for i := range nodes {
		n := &nodes[i]
		if (n.Outbound == nil && n.Xray == nil) || n.Tag == "" {
			continue
		}
		if opts.Core == "singbox" && n.Outbound == nil {
			continue // без Xray такой сервер не подключить
		}
		useXray := n.Xray != nil && opts.Core != "singbox" && (n.Outbound == nil || opts.XrayAll || opts.Core == "xray")
		if n.Outbound != nil {
			n.Outbound["tag"] = n.Tag
			linkChain(n)
		}
		splitDescription(n)
		fillMeta(n)
		tags = append(tags, n.Tag)
		if (len(allowed) == 0 || allowed[n.Category]) && !(opts.ExcludeAds && n.Ads) {
			autoTags = append(autoTags, n.Tag)
		}
		if n.Tag == selectedTag {
			found = true
		}
		switch {
		case useXray:
			proxyOutbounds = append(proxyOutbounds, xray.add(n))
		case n.Endpoint:
			endpoints = append(endpoints, n.Outbound)
		default:
			proxyOutbounds = append(proxyOutbounds, n.Outbound)
			for _, e := range n.Extra {
				proxyOutbounds = append(proxyOutbounds, e)
			}
		}
	}
	if len(tags) == 0 {
		return "", "", errors.New("нет серверов")
	}
	if len(autoTags) == 0 {
		autoTags = tags // выбранных протоколов в подписке нет — берём все
	}
	autoTags = stableAuto(autoTags, opts.AutoExclude, opts.AutoOrder)
	if !found {
		selectedTag = AutoTag
	}

	outbounds := []any{
		map[string]any{
			"type": "selector", "tag": "proxy",
			"outbounds":                   append([]string{AutoTag}, tags...),
			"default":                     selectedTag,
			"interrupt_exist_connections": prof.interrupt,
		},
		map[string]any{
			"type": "urltest", "tag": AutoTag,
			"outbounds":                   autoTags,
			"url":                         urlTestOrDefault(opts.URLTest),
			"interval":                    prof.interval,
			"tolerance":                   prof.tolerance,
			"interrupt_exist_connections": prof.interrupt,
		},
	}
	outbounds = append(outbounds, proxyOutbounds...)
	outbounds = append(outbounds, map[string]any{"type": "direct", "tag": "direct"})

	// ── сервисы ──────────────────────────────────────────────────────────
	enabled := map[string]bool{}
	for _, id := range opts.Services {
		enabled[id] = true
	}
	ids := make([]string, 0, len(services))
	for id := range services {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	var onDomains, offDomains, onCIDR, offCIDR, onUDP []string
	for _, id := range ids {
		s := services[id]
		if enabled[id] {
			onDomains = append(onDomains, s.domains...)
			onCIDR = append(onCIDR, s.cidrs...)
			if s.udpPorts != "" {
				onUDP = append(onUDP, s.udpPorts)
			}
		} else {
			offDomains = append(offDomains, s.domains...)
			offCIDR = append(offCIDR, s.cidrs...)
		}
	}
	custom := cleanDomains(opts.CustomDomains)
	direct := cleanDomains(opts.DirectDomains)
	for _, s := range opts.DirectSuffixes {
		if s = strings.Trim(strings.ToLower(strings.TrimSpace(s)), "."); s != "" && !strings.ContainsAny(s, " /:") {
			direct = append(direct, s)
		}
	}

	// ── базы правил ──────────────────────────────────────────────────────
	var ruleSets []any
	geositeRU, geoipRU, ads, adsHagezi, ruInside := "", "", "", "", ""
	if opts.Mode == ModeSmart {
		geositeRU, geoipRU = opts.rulesFile(fileGeositeRU), opts.rulesFile(fileGeoipRU)
		ruInside = opts.rulesFile(fileRuInside)
	}
	if opts.AdBlock {
		ads = opts.rulesFile(fileAds)
		adsHagezi = opts.rulesFile(fileAdsHagezi)
	}
	porn, betting := "", ""
	if opts.Kids {
		porn, betting = opts.rulesFile(filePorn), opts.rulesFile(fileBetting)
	}
	addSet := func(tag, path string) {
		if path != "" {
			ruleSets = append(ruleSets, map[string]any{"type": "local", "tag": tag, "format": "binary", "path": path})
		}
	}
	addSet("geosite-ru", geositeRU)
	addSet("geoip-ru", geoipRU)
	addSet("ru-inside", ruInside)
	addSet("ads", ads)
	addSet("ads-hagezi", adsHagezi)
	threats := ""
	if opts.BlockThreats {
		threats = opts.rulesFile(fileThreats)
	}
	addSet("threats", threats)
	addSet("porn", porn)
	addSet("betting", betting)

	// ── правила маршрутизации (сверху вниз, первое совпадение) ───────────
	rules := []any{}
	if opts.ProbePort > 1024 && opts.ProbePort < 65536 && opts.XrayAuth != "" {
		// Пользователь «direct» — замер пинга до самого сервера мимо туннеля (как с выключенным VPN)
		rules = append(rules,
			map[string]any{"inbound": []string{"probe-in"}, "auth_user": []string{"direct"}, "outbound": "direct"},
		)
		// «Пинг через прокси»: пользователь n:<tag> — соединение через этот сервер
		for _, t := range tags {
			rules = append(rules, map[string]any{"inbound": []string{"probe-in"}, "auth_user": []string{"n:" + t}, "outbound": t})
		}
		rules = append(rules, map[string]any{"inbound": []string{"probe-in"}, "outbound": "proxy"})
	}
	// Исходящие соединения Xray — сразу напрямую (до sniff и всех правил)
	if xray.dialInbound() != nil {
		rules = append(rules, map[string]any{"inbound": []string{xrayDialTag}, "outbound": "direct"})
	}
	rules = append(rules,
		map[string]any{"action": "sniff"},
		map[string]any{"protocol": "dns", "action": "hijack-dns"},
		map[string]any{"ip_is_private": true, "outbound": "direct"},
	)
	if len(opts.DirectProcesses) > 0 {
		rules = append(rules, map[string]any{"process_name": opts.DirectProcesses, "outbound": "direct"})
	}
	if opts.BlockQUIC {
		// Отказ сразу: браузер за доли секунды переходит на обычный HTTPS по TCP
		rules = append(rules, map[string]any{"protocol": "quic", "action": "reject"})
	}
	// Соединения Xray к его серверам — мимо туннеля, иначе петля
	rules = append(rules, xray.singBoxRules()...)
	// Для опытных: стратегия «узнать IP сразу» — правила по IP работают и для доменов
	if opts.DomainStrategy == "ip_on_demand" {
		rules = append(rules, map[string]any{"action": "resolve"})
	}
	// Свои правила — выше всех остальных: блок, затем VPN, затем напрямую
	userSets, _ := groupUserRules(opts.UserRules)
	if r := userSets["block"].rule(map[string]any{"action": "reject"}); r != nil {
		rules = append(rules, r)
	}
	if r := userSets["proxy"].rule(map[string]any{"outbound": "proxy"}); r != nil {
		rules = append(rules, r)
	}
	if r := userSets["direct"].rule(map[string]any{"outbound": "direct"}); r != nil {
		rules = append(rules, r)
	}
	// Свои списки «Через VPN» — после своих правил, до сервисов
	listSets, _ := groupUserRules(customListRules(opts.CustomLists))
	if r := listSets["proxy"].rule(map[string]any{"outbound": "proxy"}); r != nil {
		rules = append(rules, r)
	}
	rules = append(rules, listSets["proxy"].portRules(map[string]any{"outbound": "proxy"})...)
	// 35. Свои правила по портам: например, игры по UDP напрямую
	rules = append(rules, userSets["block"].portRules(map[string]any{"action": "reject"})...)
	rules = append(rules, userSets["proxy"].portRules(map[string]any{"outbound": "proxy"})...)
	rules = append(rules, userSets["direct"].portRules(map[string]any{"outbound": "direct"})...)
	if sets := nonEmptyTags(map[string]string{"ads": ads, "ads-hagezi": adsHagezi}); len(sets) > 0 {
		rules = append(rules, map[string]any{"rule_set": sets, "action": "reject"})
	}
	if threats != "" {
		rules = append(rules, map[string]any{"rule_set": "threats", "action": "reject"})
	}
	// 15. Семейный режим: сайты 18+ и азартные игры закрыты (плюс семейный DNS)
	if opts.Kids {
		var sets []string
		if porn != "" {
			sets = append(sets, "porn")
		}
		if betting != "" {
			sets = append(sets, "betting")
		}
		if len(sets) > 0 {
			rules = append(rules, map[string]any{"rule_set": sets, "action": "reject"})
		}
		rules = append(rules, map[string]any{"domain_suffix": gamblingSuffixes, "domain_keyword": gamblingKeywords, "action": "reject"})
	}
	if len(custom) > 0 {
		rules = append(rules, map[string]any{"domain_suffix": custom, "outbound": "proxy"})
	}
	if len(direct) > 0 {
		rules = append(rules, map[string]any{"domain_suffix": direct, "outbound": "direct"})
	}
	if r := addrRule(onDomains, onCIDR, "proxy"); r != nil {
		rules = append(rules, r)
	}
	if len(onUDP) > 0 {
		rules = append(rules, map[string]any{"network": "udp", "port_range": onUDP, "outbound": "proxy"})
	}
	// Выключенные сервисы идут напрямую во всех режимах
	if r := addrRule(offDomains, offCIDR, "direct"); r != nil {
		rules = append(rules, r)
	}
	final := "proxy"
	switch opts.Mode {
	case ModeSmart:
		rules = append(rules, map[string]any{"domain_suffix": ruDomainSuffixes, "outbound": "direct"})
		var sets []string
		if geositeRU != "" {
			sets = append(sets, "geosite-ru")
		}
		if ruInside != "" {
			sets = append(sets, "ru-inside")
		}
		if geoipRU != "" {
			sets = append(sets, "geoip-ru")
		}
		if len(sets) > 0 {
			rules = append(rules, map[string]any{"rule_set": sets, "outbound": "direct"})
		}
	case ModeSelected:
		final = "direct"
	}
	// Для опытных: «IP, если домен не подошёл» — узнаём IP и ещё раз проверяем правила по IP
	if opts.DomainStrategy == "ip_if_non_match" {
		rules = append(rules, map[string]any{"action": "resolve"})
		if len(userSets["block"].cidr) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": userSets["block"].cidr, "action": "reject"})
		}
		if len(userSets["proxy"].cidr) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": userSets["proxy"].cidr, "outbound": "proxy"})
		}
		if len(userSets["direct"].cidr) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": userSets["direct"].cidr, "outbound": "direct"})
		}
		if len(listSets["proxy"].cidr) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": listSets["proxy"].cidr, "outbound": "proxy"})
		}
		if len(onCIDR) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": onCIDR, "outbound": "proxy"})
		}
		if len(offCIDR) > 0 {
			rules = append(rules, map[string]any{"ip_cidr": offCIDR, "outbound": "direct"})
		}
		if opts.Mode == ModeSmart && geoipRU != "" {
			rules = append(rules, map[string]any{"rule_set": "geoip-ru", "outbound": "direct"})
		}
	}

	// ── DNS ───────────────────────────────────────────────────────────────
	remote := dnsServers[opts.DNS]
	if opts.DNS == "custom" {
		if c, err := customDNSServer(opts.CustomDNS); err == nil {
			remote = c
		}
	}
	if !opts.RemoteDNS.empty() && !opts.Kids {
		if r, err := dnsFromSpec(*opts.RemoteDNS); err == nil {
			remote = r
		}
	}
	if opts.Kids {
		remote = familyDNS
	}
	if remote == nil {
		remote = dnsServers["cloudflare"]
	}
	remoteServer := map[string]any{"tag": "remote", "detour": "proxy"}
	for k, v := range remote {
		remoteServer[k] = v
	}
	servers := []any{remoteServer, map[string]any{"type": "local", "tag": "local"}}
	// «Прямые» запросы: системный DNS, а в детском режиме — семейный напрямую
	directDNS := "local"
	if opts.Kids {
		fam := map[string]any{"tag": "family-direct"}
		for k, v := range familyDNS {
			fam[k] = v
		}
		servers = append(servers, fam)
		directDNS = "family-direct"
	} else if !opts.DirectDNS.empty() {
		if h, err := dnsFromSpec(*opts.DirectDNS); err == nil {
			h["tag"] = "home"
			servers = append(servers, h)
			directDNS = "home"
		}
	}
	var dnsRules []any
	if adSets := nonEmptyTags(map[string]string{"ads": ads, "ads-hagezi": adsHagezi}); len(adSets) > 0 {
		if opts.AdsDNSPort > 1024 && opts.AdsDNSPort < 65536 {
			// Счётчик заблокированной рекламы: запросы уходят в DNS приложения
			servers = append(servers, map[string]any{
				"type": "udp", "tag": "adblock", "server": "127.0.0.1", "server_port": opts.AdsDNSPort,
			})
			dnsRules = append(dnsRules, map[string]any{"rule_set": adSets, "server": "adblock"})
		} else {
			dnsRules = append(dnsRules, map[string]any{"rule_set": adSets, "action": "reject"})
		}
	}
	// Свои правила и в DNS: заблокированные не узнаём вовсе, остальные — нужным DNS
	if r := userSets["block"].domainOnly().rule(map[string]any{"action": "reject"}); r != nil {
		dnsRules = append(dnsRules, r)
	}
	if r := userSets["proxy"].domainOnly().rule(map[string]any{"server": "remote"}); r != nil {
		dnsRules = append(dnsRules, r)
	}
	if r := userSets["direct"].domainOnly().rule(map[string]any{"server": directDNS}); r != nil {
		dnsRules = append(dnsRules, r)
	}
	if r := listSets["proxy"].domainOnly().rule(map[string]any{"server": "remote"}); r != nil {
		dnsRules = append(dnsRules, r)
	}
	if len(custom) > 0 || len(onDomains) > 0 {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": append(append([]string{}, custom...), onDomains...), "server": "remote"})
	}
	if len(direct) > 0 || len(offDomains) > 0 {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": append(append([]string{}, direct...), offDomains...), "server": directDNS})
	}
	if opts.Mode == ModeSmart {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": ruDomainSuffixes, "server": directDNS})
		var ruSets []string
		if geositeRU != "" {
			ruSets = append(ruSets, "geosite-ru")
		}
		if ruInside != "" {
			ruSets = append(ruSets, "ru-inside")
		}
		if len(ruSets) > 0 {
			dnsRules = append(dnsRules, map[string]any{"rule_set": ruSets, "server": directDNS})
		}
	}
	dnsFinal := "remote"
	if opts.Mode == ModeSelected {
		dnsFinal = directDNS
	}
	// FakeDNS: сайтам через VPN отдаём поддельные IP — быстрее открываются, DNS не утекает
	if opts.FakeDNS && dnsFinal == "remote" {
		servers = append(servers, map[string]any{
			"type": "fakeip", "tag": "fakeip",
			"inet4_range": "198.18.0.0/15", "inet6_range": "fc00::/18",
		})
		dnsRules = append(dnsRules, map[string]any{"query_type": []string{"A", "AAAA"}, "server": "fakeip"})
	}
	strategy := "ipv4_only"
	if opts.IPv6 {
		strategy = "prefer_ipv4"
	}
	dnsSection := map[string]any{"servers": servers, "final": dnsFinal, "strategy": strategy}
	if len(dnsRules) > 0 {
		dnsSection["rules"] = dnsRules
	}

	// 31. WARP: всё, что шло через VPN, идёт дальше через Cloudflare WARP
	if opts.Warp != nil {
		if w, err := warpOutbound(opts.Warp); err == nil {
			endpoints = append(endpoints, w)
			throughWarp(rules)
			if final == "proxy" {
				final = warpEndpoint
			}
		}
	}

	route := map[string]any{
		"rules":                   rules,
		"final":                   final,
		"auto_detect_interface":   true,
		"default_domain_resolver": "local",
	}
	if opts.FindProcess || len(opts.DirectProcesses) > 0 {
		route["find_process"] = true
	}
	if len(ruleSets) > 0 {
		route["rule_set"] = ruleSets
	}

	inbounds := []any{map[string]any{
		"type": "tun", "tag": "tun-in",
		"address":      []string{"172.19.0.1/30", "fdfe:dcba:9876::1/126"},
		"mtu":          9000,
		"auto_route":   true,
		"strict_route": true,
		"stack":        "mixed",
	}}
	if opts.Inbound == "proxy" {
		port := opts.ProxyPort
		if port <= 1024 || port > 65535 {
			port = 2081
		}
		inbounds = []any{map[string]any{"type": "mixed", "tag": "proxy-in", "listen": "127.0.0.1", "listen_port": port}}
	} else if opts.ProxyPort > 1024 && opts.ProxyPort <= 65535 {
		// Windows в режиме TUN: тот же локальный прокси — через него приложение проверяет
		// скорость и утечки «через VPN» (само приложение идёт мимо туннеля)
		inbounds = append(inbounds, map[string]any{"type": "mixed", "tag": "proxy-in", "listen": "127.0.0.1", "listen_port": opts.ProxyPort})
	}
	if in := xray.dialInbound(); in != nil {
		inbounds = append(inbounds, in)
	}
	if opts.ProbePort > 1024 && opts.ProbePort < 65536 && opts.XrayAuth != "" {
		inbounds = append(inbounds, map[string]any{
			"type": "socks", "tag": "probe-in", "listen": "127.0.0.1", "listen_port": opts.ProbePort,
			"users": probeUsers(tags, opts.XrayAuth),
		})
	}
	if opts.Share.Enabled {
		port := opts.Share.Port
		if port <= 0 || port > 65535 {
			port = 2080
		}
		in := map[string]any{"type": "mixed", "tag": "share-in", "listen": "0.0.0.0", "listen_port": port}
		if opts.Share.Username != "" && opts.Share.Password != "" {
			in["users"] = []any{map[string]any{"username": opts.Share.Username, "password": opts.Share.Password}}
		}
		inbounds = append(inbounds, in)
	}

	cfg := map[string]any{
		"log":       logSection(opts.LogFile),
		"dns":       dnsSection,
		"inbounds":  inbounds,
		"outbounds": outbounds,
		"route":     route,
	}
	if len(endpoints) > 0 {
		cfg["endpoints"] = endpoints
	}
	if opts.ClashAPI > 0 && opts.ClashAPI < 65536 {
		// Замер задержки через работающее ядро (как «Проверить пинг» в Happ)
		cfg["experimental"] = map[string]any{"clash_api": map[string]any{
			"external_controller": fmt.Sprintf("127.0.0.1:%d", opts.ClashAPI),
			"secret":              opts.XrayAuth,
		}}
	}
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", "", err
	}
	return string(out), xray.config(), nil
}

// customDNSServer разбирает адрес DNS-over-HTTPS вида https://host[:port]/path.
func logSection(file string) map[string]any {
	l := map[string]any{"level": "warn", "timestamp": true}
	if file != "" {
		l["output"] = file
	}
	return l
}

func customDNSServer(raw string) (map[string]any, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, errors.New("пустой адрес")
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" || u.Hostname() == "" {
		return nil, errors.New("нужен адрес вида https://dns.example.com/dns-query")
	}
	s := map[string]any{"type": "https", "server": u.Hostname()}
	if p := u.Port(); p != "" {
		if n, err := strconv.Atoi(p); err == nil {
			s["server_port"] = n
		}
	}
	if u.Path != "" && u.Path != "/dns-query" {
		s["path"] = u.Path
	}
	if net.ParseIP(u.Hostname()) == nil {
		s["domain_resolver"] = "local"
	}
	return s, nil
}

// ValidateDNS возвращает "" если адрес DoH корректен, иначе текст ошибки.
func ValidateDNS(raw string) string {
	if _, err := customDNSServer(raw); err != nil {
		return err.Error()
	}
	return ""
}

func addrRule(domains, cidrs []string, outbound string) map[string]any {
	if len(domains) == 0 && len(cidrs) == 0 {
		return nil
	}
	r := map[string]any{"outbound": outbound}
	if len(domains) > 0 {
		r["domain_suffix"] = domains
	}
	if len(cidrs) > 0 {
		r["ip_cidr"] = cidrs
	}
	return r
}

// cleanDomains приводит «https://www.Site.com/path» к «site.com».
func cleanDomains(in []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, d := range in {
		if d = cleanDomain(d); d == "" || seen[d] {
			continue
		}
		seen[d] = true
		out = append(out, d)
	}
	return out
}

func cleanDomain(d string) string {
	d = strings.ToLower(strings.TrimSpace(d))
	if i := strings.Index(d, "://"); i >= 0 {
		d = d[i+3:]
	}
	if i := strings.IndexAny(d, "/?#:"); i >= 0 {
		d = d[:i]
	}
	d = strings.TrimPrefix(strings.Trim(d, "."), "www.")
	if !strings.Contains(d, ".") {
		return ""
	}
	return d
}

// stableAuto убирает из автопилота «особые» серверы (если остаются другие)
// и ставит первыми те, что были быстрее при последнем замере: до первой
// проверки sing-box берёт первый сервер группы — пусть это будет хороший.
func stableAuto(tags, exclude, order []string) []string {
	skip := map[string]bool{}
	for _, t := range exclude {
		skip[t] = true
	}
	kept := make([]string, 0, len(tags))
	for _, t := range tags {
		if !skip[t] {
			kept = append(kept, t)
		}
	}
	if len(kept) == 0 {
		kept = append(kept, tags...)
	}
	if len(order) == 0 {
		return kept
	}
	rank := map[string]int{}
	for i, t := range order {
		if _, ok := rank[t]; !ok {
			rank[t] = i
		}
	}
	sort.SliceStable(kept, func(i, j int) bool {
		ri, okI := rank[kept[i]]
		rj, okJ := rank[kept[j]]
		if okI != okJ {
			return okI
		}
		return okI && ri < rj
	})
	return kept
}

func probeUsers(tags []string, pass string) []any {
	users := []any{
		map[string]any{"username": "apex", "password": pass},
		map[string]any{"username": "direct", "password": pass},
	}
	for _, t := range tags {
		users = append(users, map[string]any{"username": "n:" + t, "password": pass})
	}
	return users
}

// urlTestOrDefault — адрес проверки автопилота: свой (http/https) или gstatic.
func urlTestOrDefault(u string) string {
	u = strings.TrimSpace(u)
	if strings.HasPrefix(u, "https://") || strings.HasPrefix(u, "http://") {
		return u
	}
	return "https://www.gstatic.com/generate_204"
}


// nonEmptyTags — метки баз, файлы которых есть, в постоянном порядке.
func nonEmptyTags(m map[string]string) []string {
	var out []string
	for _, t := range []string{"ads", "ads-hagezi"} {
		if m[t] != "" {
			out = append(out, t)
		}
	}
	return out
}
