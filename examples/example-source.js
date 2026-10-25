/*
 * @name 示例音源 (Example Source)
 * @description 演示 LX Music 自定义源协议的模板，不含任何真实解析逻辑。
 * @version 1.0.0
 * @author lxmusic-android
 * @homepage https://github.com/lyswhut/lx-music-desktop
 */

/* eslint-disable */
const { EVENT_NAMES, request, on, send } = window.lx

// Declare which sources/actions/qualities this script supports.
const sourceCapabilities = {
  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac'] },
  kg: { type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac'] },
}

// Called by LX when a track must be resolved to a stream URL.
async function handleRequest({ source, action, info }) {
  if (action === 'musicUrl') {
    // In a real source you would call the platform API here, e.g.:
    //
    //   const { body } = await new Promise((resolve, reject) => {
    //     request('https://api.example.com/song/' + info.songmid, {}, (err, resp, body) => {
    //       if (err) reject(err)
    //       else resolve({ body })
    //     })
    //   })
    //   return body.data.url
    //
    throw new Error('示例音源未实现真实解析逻辑，请导入可用的 LX 自定义源脚本')
  }
  throw new Error('unsupported action: ' + action)
}

on(EVENT_NAMES.request, handleRequest)

// Announce our capabilities to the host.
send(EVENT_NAMES.inited, {
  status: true,
  openDevTools: false,
  sources: sourceCapabilities,
})