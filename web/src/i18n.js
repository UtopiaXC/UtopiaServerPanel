import { createI18n } from 'vue-i18n';

const messages = {
  'en-US': {
    app: {
      title: 'Utopia Server Panel',
      theme: 'Toggle Theme',
      tabs: {
        summary: 'Summary',
        terminal: 'Terminal',
        logs: 'Logs'
      }
    },
    summary: {
      status: 'Server Status',
      onlinePlayers: 'Online Players',
      memoryUsage: 'Memory Usage',
      jvmMaxMemory: 'JVM Max Memory',
      jvmUsedMemory: 'JVM Used Memory',
      systemTotalMemory: 'System Total Memory',
      cpuUsage: 'CPU Usage',
      coreUsage: 'Core Usage',
      corePrefix: 'Core',
      uptime: 'Uptime',
      worldName: 'World Name',
      diskUsage: 'Disk Usage',
      diskTotal: 'Disk Total',
      diskUsed: 'Disk Used',
      gameFolderSize: 'Game Folder Size',
      version: 'Server Version',
      tps: 'TPS',
      motd: 'MOTD',
      properties: 'Server Properties',
      dataUpdated: 'Data updated at',
      loading: 'Loading...',
      propGroups: {
        world: 'World',
        gameplay: 'Gameplay',
        network: 'Network & Security'
      },
      props: {
        levelSeed: 'Seed',
        levelType: 'Level Type',
        generateStructures: 'Generate Structures',
        viewDistance: 'View Distance',
        simulationDistance: 'Simulation Dist.',
        maxBuildHeight: 'Max Build Height',
        maxWorldSize: 'Max World Size',
        gamemode: 'Gamemode',
        difficulty: 'Difficulty',
        hardcore: 'Hardcore',
        pvp: 'PvP',
        allowFlight: 'Allow Flight',
        allowNether: 'Allow Nether',
        spawnProtection: 'Spawn Protection',
        onlineMode: 'Online Mode',
        serverIp: 'Server IP',
        serverPort: 'Server Port',
        maxTickTime: 'Max Tick Time',
        whiteList: 'Whitelist',
        whitelistPlayers: 'Whitelisted Players',
        whitelistEmpty: 'No players in whitelist',
        whitelistHint: 'Hover or click \uD83D\uDC65 to view whitelisted players',
        playerIdleTimeout: 'Idle Timeout',
        maxPlayers: 'Max Players'
      }
    },
    terminal: {
      title: 'Server Console',
      placeholder: 'Enter command...',
      send: 'Send',
      webCommandPrefix: 'WEB',
      noLogs: 'No log output yet.',
      allLevels: 'All Levels'
    },
    logs: {
      title: 'Monitoring Logs',
      placeholder: 'Coming soon...'
    }
  },
  'zh-CN': {
    app: {
      title: 'Utopia 服务器面板',
      theme: '切换主题',
      tabs: {
        summary: '运行概览',
        terminal: '服务器控制台',
        logs: '监控日志'
      }
    },
    summary: {
      status: '服务器状态',
      onlinePlayers: '在线玩家',
      memoryUsage: '内存占用',
      jvmMaxMemory: 'Java 内存上限',
      jvmUsedMemory: 'Java 实际占用',
      systemTotalMemory: '系统总内存',
      cpuUsage: 'CPU占用',
      coreUsage: '核心占用',
      corePrefix: '核心',
      uptime: '运行时间',
      worldName: '世界名称',
      diskUsage: '磁盘占用',
      diskTotal: '磁盘总容量',
      diskUsed: '已占用容量',
      gameFolderSize: '游戏目录大小',
      version: '服务端版本',
      tps: 'TPS',
      motd: '服务器标语 (MOTD)',
      properties: '服务器配置',
      dataUpdated: '数据更新于',
      loading: '加载中...',
      propGroups: {
        world: '世界',
        gameplay: '游戏玩法',
        network: '网络与安全'
      },
      props: {
        levelSeed: '种子',
        levelType: '世界类型',
        generateStructures: '生成建筑',
        viewDistance: '视距',
        simulationDistance: '模拟距离',
        maxBuildHeight: '最大建造高度',
        maxWorldSize: '最大世界大小',
        gamemode: '游戏模式',
        difficulty: '难度',
        hardcore: '极限模式',
        pvp: '玩家对战',
        allowFlight: '允许飞行',
        allowNether: '允许下界',
        spawnProtection: '出生点保护',
        onlineMode: '正版验证',
        serverIp: '服务器 IP',
        serverPort: '服务器端口',
        maxTickTime: '最大 Tick 时间',
        whiteList: '白名单',
        whitelistPlayers: '白名单玩家',
        whitelistEmpty: '白名单中没有玩家',
        whitelistHint: '悬浮或点击 \uD83D\uDC65 查看白名单玩家',
        playerIdleTimeout: '闲置超时',
        maxPlayers: '最大玩家数'
      }
    },
    terminal: {
      title: '服务器控制台',
      placeholder: '输入服务器指令...',
      send: '发送',
      webCommandPrefix: 'WEB',
      noLogs: '暂无日志输出',
      allLevels: '所有等级'
    },
    logs: {
      title: '监控日志',
      placeholder: '暂未开放...'
    }
  }
};

const i18n = createI18n({
  legacy: false,
  locale: navigator.language || 'en-US',
  fallbackLocale: 'en-US',
  messages,
});

export default i18n;