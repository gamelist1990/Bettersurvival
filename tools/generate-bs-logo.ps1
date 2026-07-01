$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function New-PointArray([array]$coords, [int]$size) {
  $points = New-Object 'System.Drawing.Point[]' $coords.Count
  for ($i = 0; $i -lt $coords.Count; $i++) {
    $x = [int]($size * [double]$coords[$i][0])
    $y = [int]($size * [double]$coords[$i][1])
    $points[$i] = [System.Drawing.Point]::new($x, $y)
  }
  return $points
}

function New-RoundedRectPath([System.Drawing.Rectangle]$rect, [int]$radius) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $radius * 2
  $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
  $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
  $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
  $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
  $path.CloseFigure()
  return $path
}

function New-BSIcon([string]$path, [int]$size, [bool]$maskable = $false) {
  $bmp = New-Object System.Drawing.Bitmap $size, $size
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.Clear([System.Drawing.Color]::Transparent)

  $pad = if ($maskable) { [int]($size * 0.12) } else { [int]($size * 0.06) }
  $bgRect = [System.Drawing.Rectangle]::new($pad, $pad, $size - ($pad * 2), $size - ($pad * 2))
  $radius = [int]($size * 0.22)
  $bgPath = New-RoundedRectPath $bgRect $radius
  $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $bgRect, ([System.Drawing.Color]::FromArgb(255,199,91,57)), ([System.Drawing.Color]::FromArgb(255,35,83,71)), 45
  $g.FillPath($bgBrush, $bgPath)

  $shine = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(48,255,255,255))
  $g.FillEllipse($shine, [int]($size * 0.16), [int]($size * 0.12), [int]($size * 0.43), [int]($size * 0.30))

  $cream = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,246,238))
  $ink = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(245,23,23,23))
  $green = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,35,83,71))

  $shield = New-PointArray @(
    @(0.50,0.17), @(0.76,0.28), @(0.70,0.68), @(0.50,0.83), @(0.30,0.68), @(0.24,0.28)
  ) $size
  $g.FillPolygon($cream, $shield)

  $inner = New-PointArray @(
    @(0.50,0.24), @(0.67,0.32), @(0.63,0.62), @(0.50,0.73), @(0.37,0.62), @(0.33,0.32)
  ) $size
  $g.FillPolygon($green, $inner)

  $tree = New-PointArray @(
    @(0.50,0.26), @(0.34,0.50), @(0.44,0.50), @(0.30,0.68), @(0.46,0.68), @(0.46,0.76), @(0.54,0.76), @(0.54,0.68), @(0.70,0.68), @(0.56,0.50), @(0.66,0.50)
  ) $size
  $g.FillPolygon($ink, $tree)

  $penWidth = [Math]::Max(4, [int]($size * 0.038))
  $swordPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,199,91,57)), $penWidth
  $swordPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
  $swordPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
  $g.DrawLine($swordPen, [int]($size * 0.30), [int]($size * 0.30), [int]($size * 0.70), [int]($size * 0.70))
  $g.DrawLine($swordPen, [int]($size * 0.24), [int]($size * 0.39), [int]($size * 0.40), [int]($size * 0.23))

  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

New-Item -ItemType Directory -Force 'WebSite\public\images\brand' | Out-Null
New-Item -ItemType Directory -Force 'WebSite\public\pwa' | Out-Null
New-Item -ItemType Directory -Force 'src\main\resources' | Out-Null

New-BSIcon 'WebSite\public\pwa\icon-192.png' 192 $false
New-BSIcon 'WebSite\public\pwa\icon-512.png' 512 $false
New-BSIcon 'WebSite\public\pwa\icon-maskable-512.png' 512 $true
New-BSIcon 'WebSite\public\pwa\apple-touch-icon.png' 180 $false
New-BSIcon 'WebSite\public\images\brand\bettersurvival-logo.png' 512 $false
New-BSIcon 'src\main\resources\icon.png' 512 $false

'BS_LOGO_PNG_OK'
