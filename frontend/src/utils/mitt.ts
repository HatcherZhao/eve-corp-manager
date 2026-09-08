import mitt from 'mitt'

type Events = Record<string | symbol, any> & {
  // 自定义事件名称
  event: void
  // 任意传递的参数
}

const mittBus = mitt<Events>()
export default mittBus
