$ErrorActionPreference = "Stop"

$toolRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Join-Path $toolRoot "tapnow-localserver"

if (-not (Test-Path -LiteralPath $serverRoot)) {
    throw "未找到 Tapnow 本地服务目录：$serverRoot"
}

Set-Location $serverRoot

Write-Host "正在检查 Tapnow 本地服务依赖..."
python -m pip install -r requirements.txt

Write-Host "正在启动 Tapnow 本地服务：http://127.0.0.1:9527"
python tapnow-server-full.py
