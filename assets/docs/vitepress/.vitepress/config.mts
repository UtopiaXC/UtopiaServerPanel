import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Utopia Server Panel',
  description: 'A lightweight, multi-version web management panel mod for Minecraft servers.',
  cleanUrls: true,
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/icon.png' }]
  ],

  themeConfig: {
    logo: '/icon.png',
    search: {
      provider: 'local'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/UtopiaXC/UtopiaServerPanel' },
    ],
    footer: {
      message: 'Documentation Powered by <a href="https://vitepress.dev/" target="_blank">VitePress</a>',
      copyright: 'Copyright © <a href="https://www.utopiaxc.com/" target="_blank">UtopiaXC</a>'
    }
  },

  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      description: '适用于我的世界多版本、多加载器的轻量级 Web 服务器管理面板模组。',
      themeConfig: {
        nav: [
          { text: '首页', link: '/' },
          { text: '快速开始', link: '/introduction/getting-started' },
          { text: 'API', link: '/api/' },
          { text: 'MC百科', link: 'https://www.mcmod.cn/class/32953.html' },
          {
            text: '下载',
            items: [
              { text: 'CurseForge', link: 'https://www.curseforge.com/minecraft/mc-mods/utopia-server-panel' },
              { text: 'Modrinth', link: 'https://modrinth.com/mod/utopia-server-panel' },
              { text: 'GitHub Releases', link: 'https://github.com/UtopiaXC/UtopiaServerPanel/releases' }
            ]
          }
        ],
        sidebar: [
          {
            text: '介绍',
            items: [
              { text: '模组介绍', link: '/introduction/what-is-usp' },
              { text: '快速开始', link: '/introduction/getting-started' },
              { text: '游戏内指令', link: '/introduction/commands' }
            ]
          },
          {
            text: '用户管理与鉴权',
            items: [
              { text: '角色', link: '/auth/roles' },
              { text: '用户', link: '/auth/users' },
              { text: '玩家绑定', link: '/auth/player-binding' },
              { text: '玩家主页', link: '/auth/player-profile' },
              { text: '安全性', link: '/auth/security' }
            ]
          },
          {
            text: '面板功能',
            items: [
              { text: '看板', link: '/features/dashboard' },
              { text: '控制台', link: '/features/console' },
              { text: '性能分析', link: '/features/profiler' },
              { text: '监控日志', link: '/features/logs' },
              { text: '小地图', link: '/features/minimap' },
              { text: '模组与服务器配置', link: '/features/mods-config' },
              { text: '白名单管理', link: '/features/whitelist' },
              { text: '信息与互通', link: '/features/messages' }
            ]
          },
          {
            text: '面板设置',
            items: [
              { text: '面板设置', link: '/settings/panel' },
              { text: '游戏内联动设置', link: '/settings/ingame' },
              { text: '存档与数据库设置', link: '/settings/worlds-database' }
            ]
          },
          {
            text: 'API',
            items: [
              { text: 'API 接口文档', link: '/api/' }
            ]
          },
          {
            text: '开发',
            items: [
              { text: '项目结构', link: '/development/project-structure' },
              { text: '调试方法', link: '/development/debugging' }
            ]
          }
        ],
        outline: {
          label: '页面导航'
        },
        docFooter: {
          prev: '上一页',
          next: '下一页'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      description: 'A lightweight, multi-version web management panel mod for Minecraft servers.',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'Getting Started', link: '/en/introduction/getting-started' },
          { text: 'API', link: '/en/api/' },
          { text: 'MCMOD', link: 'https://www.mcmod.cn/class/32953.html' },
          {
            text: 'Download',
            items: [
              { text: 'CurseForge', link: 'https://www.curseforge.com/minecraft/mc-mods/utopia-server-panel' },
              { text: 'Modrinth', link: 'https://modrinth.com/mod/utopia-server-panel' },
              { text: 'GitHub Releases', link: 'https://github.com/UtopiaXC/UtopiaServerPanel/releases' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Introduction',
            items: [
              { text: 'Mod Introduction', link: '/en/introduction/what-is-usp' },
              { text: 'Getting Started', link: '/en/introduction/getting-started' },
              { text: 'Commands', link: '/en/introduction/commands' }
            ]
          },
          {
            text: 'User Management & Auth',
            items: [
              { text: 'Roles', link: '/en/auth/roles' },
              { text: 'Users', link: '/en/auth/users' },
              { text: 'Player Binding', link: '/en/auth/player-binding' },
              { text: 'Player Profile', link: '/en/auth/player-profile' },
              { text: 'Security', link: '/en/auth/security' }
            ]
          },
          {
            text: 'Panel Features',
            items: [
              { text: 'Dashboard', link: '/en/features/dashboard' },
              { text: 'Console', link: '/en/features/console' },
              { text: 'Performance Profiler', link: '/en/features/profiler' },
              { text: 'Monitoring Logs', link: '/en/features/logs' },
              { text: 'Web Minimap', link: '/en/features/minimap' },
              { text: 'Mods & Server Config', link: '/en/features/mods-config' },
              { text: 'Whitelist Management', link: '/en/features/whitelist' },
              { text: 'Messages & Chat Sync', link: '/en/features/messages' }
            ]
          },
          {
            text: 'Panel Settings',
            items: [
              { text: 'Panel Settings', link: '/en/settings/panel' },
              { text: 'In-Game Integration', link: '/en/settings/ingame' },
              { text: 'Worlds & Databases', link: '/en/settings/worlds-database' }
            ]
          },
          {
            text: 'API',
            items: [
              { text: 'API Reference', link: '/en/api/' }
            ]
          },
          {
            text: 'Development',
            items: [
              { text: 'Project Structure', link: '/en/development/project-structure' },
              { text: 'Debugging Guide', link: '/en/development/debugging' }
            ]
          }
        ]
      }
    }
  }
})
