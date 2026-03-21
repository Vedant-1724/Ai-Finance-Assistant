const hopByHopHeaders = new Set([
  'connection',
  'content-length',
  'host',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])

function getBackendBaseUrl() {
  const raw =
    process.env.API_BASE_URL ||
    process.env.VITE_API_BASE_URL ||
    process.env.BACKEND_URL ||
    ''

  return raw.replace(/\/+$/, '')
}

async function readBody(req) {
  if (req.method === 'GET' || req.method === 'HEAD') {
    return undefined
  }

  if (req.body != null) {
    if (Buffer.isBuffer(req.body)) {
      return req.body
    }

    if (typeof req.body === 'string') {
      return req.body
    }

    return JSON.stringify(req.body)
  }

  const chunks = []
  for await (const chunk of req) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  }

  return chunks.length > 0 ? Buffer.concat(chunks) : undefined
}

function copyRequestHeaders(req) {
  const headers = new Headers()

  for (const [key, value] of Object.entries(req.headers)) {
    if (value == null) {
      continue
    }

    const normalizedKey = key.toLowerCase()
    if (hopByHopHeaders.has(normalizedKey)) {
      continue
    }

    if (Array.isArray(value)) {
      headers.set(key, value.join(', '))
    } else {
      headers.set(key, value)
    }
  }

  if (req.headers.host) {
    headers.set('x-forwarded-host', req.headers.host)
  }

  return headers
}

function copyResponseHeaders(sourceHeaders, res) {
  const setCookies =
    typeof sourceHeaders.getSetCookie === 'function' ? sourceHeaders.getSetCookie() : []

  if (setCookies.length > 0) {
    res.setHeader('set-cookie', setCookies)
  }

  for (const [key, value] of sourceHeaders.entries()) {
    if (key.toLowerCase() === 'set-cookie' || hopByHopHeaders.has(key.toLowerCase())) {
      continue
    }

    res.setHeader(key, value)
  }
}

export default async function handler(req, res) {
  const backendBaseUrl = getBackendBaseUrl()

  if (!backendBaseUrl) {
    res.statusCode = 500
    res.setHeader('content-type', 'application/json')
    res.end(
      JSON.stringify({
        error: 'API_PROXY_MISCONFIGURED',
        message: 'Set API_BASE_URL, VITE_API_BASE_URL, or BACKEND_URL in Vercel.',
      })
    )
    return
  }

  const pathParam = req.query?.path
  const path = Array.isArray(pathParam)
    ? pathParam.join('/')
    : typeof pathParam === 'string'
      ? pathParam
      : ''
  const queryIndex = req.url.indexOf('?')
  const query = queryIndex >= 0 ? req.url.slice(queryIndex) : ''
  const targetUrl = `${backendBaseUrl}/api/${path}${query}`

  try {
    const upstreamResponse = await fetch(targetUrl, {
      method: req.method,
      headers: copyRequestHeaders(req),
      body: await readBody(req),
      redirect: 'manual',
    })

    copyResponseHeaders(upstreamResponse.headers, res)
    res.statusCode = upstreamResponse.status

    const buffer = Buffer.from(await upstreamResponse.arrayBuffer())
    res.end(buffer)
  } catch (error) {
    res.statusCode = 502
    res.setHeader('content-type', 'application/json')
    res.end(
      JSON.stringify({
        error: 'API_PROXY_REQUEST_FAILED',
        message: error instanceof Error ? error.message : 'Unknown proxy error.',
      })
    )
  }
}
