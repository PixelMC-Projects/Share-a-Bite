# Share a Bite / 分你一口

[English](#english) · [中文](#中文)

## English

A standalone, lightweight player-to-player hold-to-feed mod for **Minecraft 1.20.1 (Forge)** with recipient permissions, anti-grief feeding notices, native eating feedback, and automatic food-progression compatibility.

### Features

- **Zero external mod dependencies**: Works standalone out of the box—no companion feeding mod or food mod required.
- **Hold right-click to feed**: Hold right-click on another player with food in your main hand; keep holding to feed another portion. Releasing, looking away, or switching slots cancels without consuming food.
- **Anti-grief recipient permissions & live notice**:
  - Recipients see a subtle ActionBar warning (`<player> is feeding you <food>`) while a feed is charging, giving them time to step away from prank foods.
  - Built-in scrollable permissions screen (unbound keybind by default) lets each player choose exactly who may feed them, persisted per world by UUID.
- **Native eating feedback**: Plays soft vanilla eating/drinking sounds and food-crumb particles near the recipient during charging and on completion.
- **Automatic food-mod compatibility**: Fires Forge’s standard `onItemUseFinish` event for the recipient, so mods like **Spice of Life: Carrot Edition**, **Farmer’s Delight**, and **Diet** automatically credit discoveries, bonuses, and container returns (bowls/bottles).
- **Server configuration** (`shareabite-server.toml` + item tag `#shareabite:unfeedable`):
  - `maxTargetHunger` (default `20`, set to `21` to allow feeding at full hunger)
  - `allowAlwaysEdibleWhenFull` (default `true`, lets always-edible items like Golden Apples or Honey Bottles be fed at full hunger)
  - `requireSneakToFeed` (default `false`, require sneaking while holding right-click)
  - `holdDurationMultiplier` (default `1.0`) & `targetCooldownTicks` (default `20`)
  - `foodBlacklist` (item IDs) plus datapack tag `#shareabite:unfeedable`

### Installation & Usage

Install on **both the server and clients** (Minecraft `1.20.1` / Forge `47.3.33+`).

1. Hold any edible item in your main hand, look at another player, and hold right-click.
2. Bind **Open feeding permissions** in Controls to manage who is allowed to feed you.

---

## 中文

一款面向 **Minecraft 1.20.1（Forge）** 的轻量独立玩家互相投喂模组：长按右键投喂、被喂方防恶搞提醒与个人许可面板、原版进食音效粒子反馈，并天然兼容食物书等各类食物模组。

### 特性

- **零外部模组硬依赖**：开箱即用，无需额外安装任何前置喂食模组或食物模组。
- **长按右键投喂**：主手手持食物对准其他玩家长按右键即可喂食，持续按住可连续投喂下一份；中途松手、移开准星或切换物品栏会自动取消且不消耗食物。
- **防恶搞提醒与个人许可面板**：
  - 读条期间被喂方底部会收到轻提示（`xxx 正在喂你 [食物]`），防止被恶搞强喂腐肉/河豚。
  - 内置可翻页搜索的许可面板（快捷键默认未绑定，可在按键设置中配置），每位玩家可独立勾选允许谁给自己喂食，按 UUID 持久化保存。
- **原版进食反馈**：读条与喂食完成时在被喂玩家嘴边播放轻柔的咀嚼/饮用音效与对应食物碎屑粒子。
- **自动兼容主流食物模组**：喂食完成时按标准 Forge 进食事件触发，装有 **生活调味料：胡萝卜版（SOL: Carrot）**、**农夫乐事**、**Diet** 等模组时会自动计入食物进度、效果与碗/瓶容器返还。
- **简洁服务端配置**（`shareabite-server.toml` + 数据包标签 `#shareabite:unfeedable`）：
  - `maxTargetHunger`（默认 `20`，设为 `21` 允许满饱食度尝鲜）
  - `allowAlwaysEdibleWhenFull`（默认 `true`，允许金苹果、蜂蜜瓶等满饱食度可食用物品直接投喂）
  - `requireSneakToFeed`（默认 `false`，开启后需潜行+长按右键才能喂人）
  - `holdDurationMultiplier`（读条时长倍率，默认 `1.0`）与 `targetCooldownTicks`（防刷冷却，默认 `20` tick）
  - `foodBlacklist`（食物黑名单物品 ID）与数据包物品标签 `#shareabite:unfeedable`

### 安装与使用

服务端与客户端均需安装（Minecraft `1.20.1` / Forge `47.3.33+`）。主手拿食物对准玩家长按右键即可喂食；可在「按键设置」中为「打开喂食许可面板」绑定快捷键。

---

Original author / 原作者: [zcpu · PixelMC](https://github.com/PixelMC-Projects) · Licensed under [Apache-2.0](LICENSE).
