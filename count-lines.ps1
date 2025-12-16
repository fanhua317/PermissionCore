param(
    [string]$Root = (Get-Location).Path,
    [string[]]$Exts = @('java','ts','tsx','js','jsx','sql','vue','html'),
    [string[]]$ExcludeDirs = @('node_modules','target','.git','.idea'),
    [int]$TopFiles = 20
)

# 规范化 Root
if (-not (Test-Path -Path $Root)) {
    Write-Error "指定路径不存在: $Root"
    exit 1
}

# 构建排除目录正则（忽略大小写），用于匹配路径中含有排除目录名
$excludePattern = ($ExcludeDirs | ForEach-Object { [regex]::Escape($_) }) -join '|'
$excludeRegex = "(?i)(\\|/)(?:$excludePattern)(\\|/)"

# 获取文件（排除常见输出/依赖目录）
$files = Get-ChildItem -Path $Root -Recurse -File -ErrorAction SilentlyContinue |
         Where-Object {
             # 跳过在排除目录下的文件（使用 -match 更鲁棒）
             -not ($_.FullName -match $excludeRegex)
         } |
         Where-Object { $Exts -contains ($_.Extension.TrimStart('.').ToLower()) }

if (-not $files -or $files.Count -eq 0) {
    Write-Host "未在路径 '$Root' 下找到匹配的文件。检查是否传入了正确的 Root 或 Exts。" -ForegroundColor Yellow
    exit 0
}

# 初始化统计表
$perExt = @{}
foreach ($e in $Exts) { $perExt[$e] = 0 }
$total = 0

# 用于记录每个文件行数以输出 top N
$fileLines = @()

foreach ($f in $files) {
    try {
        $content = Get-Content -Raw -Encoding UTF8 -ErrorAction Stop $f.FullName
    } catch {
        Write-Host "跳过无法读取: $($f.FullName)" -ForegroundColor DarkYellow
        continue
    }

    # 如果内容为 null 或空，设为空字符串以避免 Replace 出错
    if ($null -eq $content) { $text = '' } else { $text = [string]$content }

    # 移除注释（简单正则，不会在字符串内安全处理所有情况）
    $text = [regex]::Replace($text, '(?s)/\*.*?\*/', '')
    $text = [regex]::Replace($text, '(?s)<!--.*?-->', '')
    $text = [regex]::Replace($text, '(?m)//.*$', '')
    $text = [regex]::Replace($text, '(?m)--.*$', '')
    $text = [regex]::Replace($text, '(?m)^\s*#.*$', '')

    # 按行分割并计数非空行
    $count = ($text -split "\r?\n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }).Count

    $ext = $f.Extension.TrimStart('.').ToLower()
    if (-not $perExt.ContainsKey($ext)) { $perExt[$ext] = 0 }
    $perExt[$ext] += $count
    $total += $count

    $fileLines += [PSCustomObject]@{ Path = $f.FullName; Lines = $count; Ext = $ext }
}

Write-Host "扫描路径: $Root"
Write-Host "文件数: $($files.Count)"
Write-Host "按扩展名统计 (有效代码行数):"
$perExt.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-8} : {1}" -f $_.Key, $_.Value) }
Write-Host "总有效代码行数: $total"

# 输出 top N 最大文件
Write-Host "`n前 $TopFiles 个按有效代码行数排序的文件："
$fileLines | Sort-Object -Property Lines -Descending | Select-Object -First $TopFiles | ForEach-Object {
    Write-Host ("{0,6}  {1}" -f $_.Lines, $_.Path)
}

# 返回结构化对象到管道（便于脚本调用时进一步处理）
return [PSCustomObject]@{
    Root = $Root
    FilesCount = $files.Count
    PerExt = $perExt
    TotalLines = $total
    TopFiles = ($fileLines | Sort-Object -Property Lines -Descending | Select-Object -First $TopFiles)
}
