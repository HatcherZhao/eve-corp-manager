/**
 * 使用前端同一套 RSA 加密、租户请求头和图形验证码流程验证本地真实账号登录。
 * 密码仅从环境变量读取，脚本不会输出密码、验证码或会话令牌。
 */
import { constants, createPublicKey, publicEncrypt } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const baseUrl = 'http://127.0.0.1:8000'
const username = process.env.EVE_LOGIN_USERNAME || 'zhaoyuqing_test@163.com'
const tenantName = process.env.EVE_LOGIN_TENANT_NAME || '穆塔根商人协会'
const clientId = 'ef51c9a3e9046c4f2ea45142c8a8344a'
const password = process.env.EVE_LOGIN_PASSWORD

if (!password) {
  throw new Error('缺少 EVE_LOGIN_PASSWORD，无法执行真实登录验证。')
}

const envFile = readFileSync(new URL('../../frontend/.env.local', import.meta.url), 'utf8')
const publicKeyBase64 = envFile.match(/^VITE_RSA_PUBLIC_KEY=(.+)$/m)?.[1]
if (!publicKeyBase64) {
  throw new Error('未找到前端 RSA 公钥配置。')
}

const captchaResponse = await fetch(`${baseUrl}/captcha/image`)
if (!captchaResponse.ok) {
  throw new Error(`获取验证码失败（HTTP ${captchaResponse.status}）。`)
}
const captchaPayload = await captchaResponse.json()
const captchaData = captchaPayload.data
if (!captchaData?.uuid || !captchaData.isEnabled) {
  throw new Error('服务未返回可用的图形验证码。')
}

const captchaValue = execFileSync('redis-cli', ['-n', '14', '--raw', 'GET', `CAPTCHA:${captchaData.uuid}`], {
  encoding: 'utf8',
}).trim()
if (!captchaValue || captchaValue === '(nil)') {
  throw new Error('验证码未写入 Redis db14。')
}
// RedisUtils 使用 JsonJacksonCodec 保存字符串，redis-cli 读取到的是 JSON 字符串字面量。
const captcha = JSON.parse(captchaValue)

const publicKey = createPublicKey({
  key: Buffer.from(publicKeyBase64, 'base64'),
  format: 'der',
  type: 'spki',
})
const encryptedPassword = publicEncrypt({ key: publicKey, padding: constants.RSA_PKCS1_PADDING }, Buffer.from(password))
  .toString('base64')
const loginResponse = await fetch(`${baseUrl}/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    ...(tenantName ? { 'X-Tenant-Code': encodeURIComponent(tenantName) } : {}),
  },
  body: JSON.stringify({
    authType: 'ACCOUNT',
    clientId,
    username,
    password: encryptedPassword,
    captcha,
    uuid: captchaData.uuid,
  }),
})
const loginPayload = await loginResponse.json()
if (!loginResponse.ok || !loginPayload?.data?.token || !loginPayload.data.tenantId) {
  throw new Error(`登录失败：${loginPayload?.msg || `HTTP ${loginResponse.status}`}。`)
}

const sessionHeaders = {
  Authorization: `Bearer ${loginPayload.data.token}`,
  'X-Tenant-Id': String(loginPayload.data.tenantId),
}
const [userInfoResponse, routesResponse] = await Promise.all([
  fetch(`${baseUrl}/auth/user/info`, { headers: sessionHeaders }),
  fetch(`${baseUrl}/auth/user/route`, { headers: sessionHeaders }),
])
const [userInfoPayload, routesPayload] = await Promise.all([userInfoResponse.json(), routesResponse.json()])
if (!userInfoResponse.ok || !userInfoPayload?.data || !routesResponse.ok || !Array.isArray(routesPayload?.data)) {
  throw new Error('登录后的用户资料或动态路由请求失败。')
}

console.log(`真实登录及页面初始化验证通过：租户 ${loginPayload.data.tenantId}，用户资料和 ${routesPayload.data.length} 条顶级路由均可读取。`)
