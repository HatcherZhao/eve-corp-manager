const EVE_LOGOUT_URL = 'https://login.evepc.163.com/account/logoff'
const POPUP_NAME = 'eve-serenity-authorization'
const POPUP_WIDTH = 960
const POPUP_HEIGHT = 680

/** 创建居中的国服授权窗口，必须在用户点击事件中调用以避免被浏览器拦截。 */
export function prepareEveAuthorizationWindow(): Window | null {
  const left = Math.max(0, window.screenX + (window.outerWidth - POPUP_WIDTH) / 2)
  const top = Math.max(0, window.screenY + (window.outerHeight - POPUP_HEIGHT) / 2)
  const features = [
    'popup=yes',
    `width=${POPUP_WIDTH}`,
    `height=${POPUP_HEIGHT}`,
    `left=${Math.round(left)}`,
    `top=${Math.round(top)}`,
    'resizable=yes',
    'scrollbars=yes',
    'toolbar=no',
    'menubar=no',
    'location=yes',
    'status=no',
  ].join(',')
  return window.open('', POPUP_NAME, features)
}

/** 将已创建的小窗口导航到国服授权页，并断开 opener 降低跨站跳转风险。 */
export function navigateEveAuthorizationWindow(popup: Window | null, authorizationUri: string): boolean {
  if (!popup || popup.closed) return false
  popup.opener = null
  popup.location.replace(authorizationUri)
  popup.focus()
  return true
}

/**
 * 重新授权前退出当前网易登录态，再由官方退出页回跳到本次唯一授权地址。
 *
 * 这只处理网易 EVE 登录会话，不会宣称或尝试清除用户浏览器的全部缓存。
 */
export function navigateEveReauthorizationWindow(popup: Window | null, authorizationUri: string): boolean {
  const logoutUrl = new URL(EVE_LOGOUT_URL)
  logoutUrl.searchParams.set('returnUrl', authorizationUri)
  return navigateEveAuthorizationWindow(popup, logoutUrl.toString())
}

/** 关闭尚未进入授权流程的空窗口。 */
export function closePreparedEveAuthorizationWindow(popup: Window | null) {
  if (popup && !popup.closed) popup.close()
}

/** 可选打开网易退出页，用于切换账号或清理残留的网易登录态。 */
export function openEveAccountLogoutWindow(): Window | null {
  const popup = prepareEveAuthorizationWindow()
  return navigateEveAuthorizationWindow(popup, EVE_LOGOUT_URL) ? popup : null
}
