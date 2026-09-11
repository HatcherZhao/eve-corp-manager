import axios from 'axios'
import qs from 'query-string'
import type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { useTenantStore } from '@/stores/modules/tenant'
import { useUserStore } from '@/stores'
import { getToken } from '@/utils/auth'
import modalErrorWrapper from '@/utils/modal-error-wrapper'
import messageErrorWrapper from '@/utils/message-error-wrapper'
import notificationErrorWrapper from '@/utils/notification-error-wrapper'
import router from '@/router'

interface ICodeMessage {
  [propName: number]: string
}

const StatusCodeMessage: ICodeMessage = {
  200: '服务器成功返回请求的数据',
  201: '新建或修改数据成功。',
  202: '一个请求已经进入后台排队（异步任务）',
  204: '删除数据成功',
  400: '请求错误(400)',
  401: '登录已过期，请重新登录',
  403: '拒绝访问(403)',
  404: '请求出错(404)',
  408: '请求超时(408)',
  500: '服务器错误(500)',
  501: '服务未实现(501)',
  502: '网络错误(502)',
  503: '服务不可用(503)',
  504: '网络超时(504)',
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL,
  timeout: 30 * 1000,
})

const handleError = (msg: string) => {
  if (msg.length >= 15) {
    return notificationErrorWrapper({
      content: msg || '服务器端错误',
      duration: 5 * 1000,
    })
  }
  return messageErrorWrapper({
    content: msg || '服务器端错误',
    duration: 5 * 1000,
  })
}

/** 统一引导已过期的本站会话重新登录，避免把认证问题误报为服务故障。 */
let redirectingToLogin = false
const handleLoginExpired = () => {
  if (redirectingToLogin) return
  redirectingToLogin = true
  modalErrorWrapper({
    title: '登录已过期',
    content: '你已超过 6 小时未操作。重新登录后会回到当前页面。',
    maskClosable: false,
    escToClose: false,
    okText: '重新登录',
    async onOk() {
      try {
        const userStore = useUserStore()
        await userStore.logoutCallBack()
        const currentPath = router.currentRoute.value.fullPath
        await router.replace(`/login?redirect=${encodeURIComponent(currentPath)}`)
      } finally {
        redirectingToLogin = false
      }
    },
  })
}

// 请求拦截器
http.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 登录和图形验证码必须脱离旧会话：登录由本次填写的军团名称解析租户，验证码则完全无需身份。
    const isAnonymousRequest = config.url === '/auth/login' || config.url === '/captcha/image'
    const token = getToken()
    if (!config.headers) {
      config.headers = {}
    }
    if (token && !isAnonymousRequest) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const tenantStore = useTenantStore()
    if (!isAnonymousRequest && tenantStore.tenantEnabled && tenantStore.tenantId) {
      config.headers['X-Tenant-Id'] = tenantStore.tenantId
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器
http.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response
    const { success, code, msg } = data

    if (response.request.responseType === 'blob') {
      const contentType = data.type
      if (contentType.startsWith('application/json')) {
        const reader = new FileReader()
        reader.readAsText(data)
        reader.onload = () => {
          const { success, msg } = JSON.parse(reader.result as string)
          if (!success) {
            handleError(msg)
          }
        }
        return Promise.reject(msg)
      } else {
        return response
      }
    }

    if (success) {
      return response
    }

    // Token 失效
    if (code === '401' && response.config.url !== '/auth/user/info') {
      handleLoginExpired()
    } else {
      handleError(msg)
    }
    return Promise.reject(new Error(msg || '服务器端错误'))
  },
  (error: AxiosError) => {
    if (!error.response) {
      handleError('网络连接失败，请检查您的网络')
      return Promise.reject(error)
    }
    const status = error.response?.status
    if (status === 401) {
      handleLoginExpired()
      return Promise.reject(error)
    }
    const errorMsg = StatusCodeMessage[status] || '服务器暂时未响应，请刷新页面并重试。若无法解决，请联系管理员'
    handleError(errorMsg)
    return Promise.reject(error)
  },
)

const request = async <T = unknown>(config: AxiosRequestConfig): Promise<ApiRes<T>> => {
  return http.request<T>(config)
    .then((res: AxiosResponse) => res.data)
    .catch((err: { msg: string }) => Promise.reject(err))
}

const requestNative = async <T = unknown>(config: AxiosRequestConfig): Promise<AxiosResponse> => {
  return http.request<T>(config)
    .then((res: AxiosResponse) => res)
    .catch((err: { msg: string }) => Promise.reject(err))
}

const createRequest = (method: string) => {
  return <T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<ApiRes<T>> => {
    return request({
      method,
      url,
      [method === 'get' ? 'params' : 'data']: params,
      ...(method === 'get'
        ? {
            paramsSerializer: (obj) => qs.stringify(obj),
          }
        : {}),
      ...config,
    })
  }
}

const download = (url: string, params?: object, config?: AxiosRequestConfig): Promise<AxiosResponse> => {
  return requestNative({
    method: 'get',
    url,
    responseType: 'blob',
    params,
    paramsSerializer: (obj) => qs.stringify(obj),
    ...config,
  })
}

export default {
  get: createRequest('get'),
  post: createRequest('post'),
  put: createRequest('put'),
  patch: createRequest('patch'),
  del: createRequest('delete'),
  request,
  requestNative,
  download,
}
