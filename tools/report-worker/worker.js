/**
 * APEX VPN — приём отчётов из приложения и пересылка в Telegram.
 * Cloudflare Worker: без своего сервера, бесплатно.
 *
 * Секреты (Settings → Variables and Secrets), в код их НЕ вписывать:
 *   BOT_TOKEN — токен бота от @BotFather
 *   CHAT_ID   — куда слать (ваш id от @userinfobot или id группы поддержки)
 *
 * Приложение шлёт POST с JSON: { text, note, version, device, id }
 *   text    — сам отчёт (без ссылки подписки, ключей и IP)
 *   note    — что написал пользователь
 *   id      — случайный id установки (для лимита, не личные данные)
 */

const MAX_TEXT = 60_000      // размер отчёта, символов
const MAX_NOTE = 1_000       // комментарий пользователя
const PER_HOUR = 3           // отчётов в час с одной установки
const hits = new Map()       // id → [время, …] (живёт, пока жив экземпляр Worker'а)

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    if (request.method === 'GET' && url.pathname === '/') {
      return new Response('APEX VPN report: ok', { headers: { 'content-type': 'text/plain; charset=utf-8' } })
    }
    if (request.method !== 'POST' || url.pathname !== '/report') return reply(404, 'not found')
    if (!env.BOT_TOKEN || !env.CHAT_ID) return reply(500, 'не настроены BOT_TOKEN / CHAT_ID')

    // Простые проверки: это приложение APEX и разумный размер
    const ua = request.headers.get('user-agent') || ''
    if (!ua.includes('APEX VPN')) return reply(403, 'forbidden')
    const len = Number(request.headers.get('content-length') || 0)
    if (len > MAX_TEXT * 4 + 8_000) return reply(413, 'слишком большой отчёт')

    let body
    try { body = await request.json() } catch { return reply(400, 'нужен JSON') }
    const text = String(body.text || '').slice(0, MAX_TEXT)
    const note = String(body.note || '').slice(0, MAX_NOTE).trim()
    const version = String(body.version || '?').slice(0, 40)
    const device = String(body.device || '?').slice(0, 80)
    const id = String(body.id || request.headers.get('cf-connecting-ip') || 'anon').slice(0, 64)
    if (!text.startsWith('APEX VPN')) return reply(400, 'это не отчёт APEX VPN')

    // Лимит: не больше PER_HOUR отчётов в час с одной установки
    const now = Date.now()
    const list = (hits.get(id) || []).filter(t => now - t < 3_600_000)
    if (list.length >= PER_HOUR) return reply(429, 'слишком часто — попробуйте через час')
    list.push(now)
    hits.set(id, list)

    const caption = [
      '🆘 Отчёт APEX VPN ' + version,
      '📱 ' + device,
      note ? '💬 ' + note : '💬 (без комментария)',
      '🔖 ' + id.slice(0, 8),
    ].join('\n').slice(0, 1000)

    const form = new FormData()
    form.append('chat_id', env.CHAT_ID)
    form.append('caption', caption)
    form.append('document', new Blob([text], { type: 'text/plain' }), 'apex-report-' + version + '.txt')
    const tg = await fetch('https://api.telegram.org/bot' + env.BOT_TOKEN + '/sendDocument', { method: 'POST', body: form })
    if (!tg.ok) return reply(502, 'Telegram не принял отчёт: ' + tg.status)
    return reply(200, 'ok')
  },
}

function reply(status, message) {
  return new Response(JSON.stringify({ ok: status === 200, message }), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  })
}
