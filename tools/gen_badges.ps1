Add-Type -AssemblyName System.Drawing

$OutDir = 'G:\插件\Title\src\main\resources\images'
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

$w = 80; $h = 24; $r = 8

$badges = @(
  @{ name = 'star';    text = '星辰'; c1 = '#1b2a6b'; c2 = '#4dd0e1'; accent = '#cfe9ff' },
  @{ name = 'flame';   text = '烈焰'; c1 = '#6b1a1a'; c2 = '#ff9a3c'; accent = '#ffd9a0' },
  @{ name = 'orchid';  text = '幽兰'; c1 = '#0f2f2a'; c2 = '#35d0a0'; accent = '#b8ffe8' },
  @{ name = 'aurora';  text = '极光'; c1 = '#12224a'; c2 = '#b06bff'; accent = '#d9c4ff' },
  @{ name = 'flower';  text = '花语'; c1 = '#4a1533'; c2 = '#ff77b0'; accent = '#ffc9df' },
  @{ name = 'thunder'; text = '雷帝'; c1 = '#241145'; c2 = '#9b5cff'; accent = '#e2d0ff' },
  @{ name = 'supreme'; text = '至尊'; c1 = '#3a2a00'; c2 = '#ffe08a'; accent = '#fff3c4' },
  @{ name = 'night';   text = '夜莺'; c1 = '#101a2c'; c2 = '#5d7cff'; accent = '#c3d2ff' },
  @{ name = 'bounty';  text = '丰饶'; c1 = '#243b0f'; c2 = '#b6e05a'; accent = '#e6ffb8' }
)

foreach ($b in $badges) {
  $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $path.AddArc(0, 0, $r * 2, $r * 2, 180, 90)
  $path.AddArc($w - $r * 2 - 1, 0, $r * 2, $r * 2, 270, 90)
  $path.AddArc($w - $r * 2 - 1, $h - $r * 2 - 1, $r * 2, $r * 2, 0, 90)
  $path.AddArc(0, $h - $r * 2 - 1, $r * 2, $r * 2, 90, 90)
  $path.CloseFigure()
  $g.SetClip($path)

  $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.ColorTranslator]::FromHtml($b.c1), [System.Drawing.ColorTranslator]::FromHtml($b.c2), 0)
  $g.FillRectangle($brush, $rect)

  $hl = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(70, 255, 255, 255), [System.Drawing.Color]::FromArgb(0, 255, 255, 255), 90)
  $topHalf = New-Object System.Drawing.Rectangle(0, 0, $w, [int]($h / 2))
  $g.FillRectangle($hl, $topHalf)

  $g.ResetClip()
  $pen = New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml($b.accent), 1.2)
  $g.DrawPath($pen, $path)

  $font = New-Object System.Drawing.Font('Microsoft YaHei', 13, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $fmt = New-Object System.Drawing.StringFormat
  $fmt.Alignment = [System.Drawing.StringAlignment]::Center
  $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
  $shadow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(160, 0, 0, 0))
  $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
  $rfShadow = New-Object System.Drawing.RectangleF(1, 2, $w, $h)
  $rfText = New-Object System.Drawing.RectangleF(0, 1, $w, $h)
  $g.DrawString($b.text, $font, $shadow, $rfShadow, $fmt)
  $g.DrawString($b.text, $font, $white, $rfText, $fmt)

  $g.Dispose()
  $target = Join-Path $OutDir ('badge_' + $b.name + '.png')
  $bmp.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output ($b.name + ' -> ' + (Get-Item $target).Length + ' bytes')
}
