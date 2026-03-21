const backendBaseUrl = (
  process.env.API_BASE_URL ||
  process.env.VITE_API_BASE_URL ||
  process.env.BACKEND_URL ||
  ''
).replace(/\/+$/, '')

const rewrites = [
  ...(backendBaseUrl
    ? [
        {
          source: '/api/:path*',
          destination: `${backendBaseUrl}/api/:path*`,
        },
        {
          source: '/actuator/:path*',
          destination: `${backendBaseUrl}/actuator/:path*`,
        },
      ]
    : []),
  {
    source: '/(.*)',
    destination: '/index.html',
  },
]

export const config = {
  $schema: 'https://openapi.vercel.sh/vercel.json',
  rewrites,
}
