---
name: list-to-csv
display: 列表收成表格
description: 用现有的逐屏采集得到行，写成 CSV 交给用户
aliases: ["收成表格", "导出 CSV"]
terms: ["列表", "表格", "CSV"]
---
## 入口

屏幕上已经是一份列表，或用户要的就是把看见的行收成表。

## 步骤

1. 用 `phone_observe` 看当前屏，用 `phone_list_elements` 把这一屏的行记下来。翻到下一屏再采，直到没有新行。
2. 把行写成 JSON 数组，每行一个对象，用 `write_file` 存到 `/workspace/rows.json`。
3. 用 `run_bundled_script` 跑 `scripts/rows_to_csv.py`，source 填 pack:list-to-csv，args 为 ["rows.json", "table.csv"]。
4. 做好的表用 `share_file` 交给用户。

## 容易出错的地方

- 翻页前后同一行会重复，写入 JSON 前按原文去重。
- 脚本退出码不是 0 时不要再 invent 一行命令去改文件。

## 怎样算办成(核实证据)

`share_file` 成功，下载/Mika 里出现 CSV，行数和采到的去重后行数一致。

## 什么时候停下交给用户

列表在别的 App 里要登录、或一屏都认不出行时，先问用户。
