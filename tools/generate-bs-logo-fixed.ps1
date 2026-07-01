$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function New-Color([int]$a, [int]$r, [int]$g, [int]$b) {
  return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function New-Point([int]$size, [double]$x, [double]$y) {
  return [System.Drawing.Point]::new([int]($size * $x), [int]($size * $y))
}

function New-PointArray([int]$size, [object[]]$coords) {
  $points = New-Object 'System.Drawing.Point[]' $coords.Count
  for ($i = 0; $i -lt $coords.Count; $i++) {
    $points[$i] = New-Point $size ([double]$coords[$i][0]) ([double]$coords[$i][1])
  }
  return $points
}

function New-RoundedRectPath([int]$x, [int]$y, [int]$w, [int]$h, [int]$radius) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $radius * 2
  $path.AddArc($x, $y, $d, $d, 180, 90)
  $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
  $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
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
  $box = $size - ($pad * 2)
  $bgRect = [System.Drawing.Rectangle]::new($pad, $pad, $box, $box)
  $bgPath = New-RoundedRectPath $pad $pad $box $box ([int]($size * 0.22))
  $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $bgRect, (New-Color 255 199 91 57), (New-Color 255 35 83 71), 45
  $g.FillPath($bgBrush, $bgPath)

  $shine = New-Object System.Drawing.SolidBrush (New-Color 48 255 255 255)
  $g.FillEllipse($shine, [int]($size * 0.16), [int]($size * 0.12), [int]($size * 0.43), [int]($size * 0.30))

  $cream = New-Object System.Drawing.SolidBrush (New-Color 255 255 246 238)
  $green = New-Object System.Drawing.SolidBrush (New-Color 255 35 83 71)
  $ink = New-Object System.Drawing.SolidBrush (New-Color 245 23 23 23)

  $shield = New-PointArray $size @(
    @(0.50, 0.16), @(0.76, 0.28), @(0.70, 0.67), @(0.50, 0.84), @(0.30, 0.67), @(0.24, 0.28)
  )
  $g.FillPolygon($cream, $shield)

  $inner = New-PointArray $size @(
    @(0.50, 0.24), @(0.66, 0.32), @(0.62, 0.61), @(0.50, 0.72), @(0.38, 0.61), @(0.34, 0.32)
  )
  $g.FillPolygon($green, $inner)

  $tree = New-PointArray $size @(
    @(0.50, 0.27), @(0.34, 0.50), @(0.44, 0.50), @(0.30, 0.68), @(0.46, 0.68), @(0.46, 0.76), @(0.54, 0.76), @(0.54, 0.68), @(0.70, 0.68), @(0.56, 0.50), @(0.66, 0.50)
  )
  $g.FillPolygon($ink, $tree)

  $penWidth = [Math]::Max(4, [int]($size * 0.038))
  $swordPen = New-Object System.Drawing.Pen (New-Color 255 199 91 57), $penWidth
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
New-Item -ItemType Directory -Force 'bin\main' | Out-Null

New-BSIcon 'WebSite\public\pwa\icon-192.png' 192 $false
New-BSIcon 'WebSite\public\pwa\icon-512.png' 512 $false
New-BSIcon 'WebSite\public\pwa\icon-maskable-512.png' 512 $true
New-BSIcon 'WebSite\public\pwa\apple-touch-icon.png' 180 $false
New-BSIcon 'WebSite\public\images\brand\bettersurvival-logo.png' 512 $false
New-BSIcon 'src\main\resources\icon.png' 512 $false
Copy-Item 'src\main\resources\icon.png' 'bin\main\icon.png' -Force

$svg = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-label="BetterSurvival logo">
  <defs><linearGradient id="bs-bg" x1="72" y1="72" x2="440" y2="440" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#c75b39"/><stop offset="1" stop-color="#235347"/></linearGradient></defs>
  <rect x="32" y="32" width="448" height="448" rx="112" fill="url(#bs-bg)"/>
  <ellipse cx="192" cy="128" rx="112" ry="72" fill="#ffffff" opacity=".18"/>
  <path d="M256 82 389 143 358 343 256 425 154 343 123 143Z" fill="#fff6ee"/>
  <path d="M256 123 343 164 322 318 256 374 190 318 169 164Z" fill="#235347"/>
  <path d="M256 133 174 256h51l-72 92h82v43h42v-43h82l-72-92h51Z" fill="#171717"/>
  <path d="M154 154 358 358" stroke="#c75b39" stroke-width="20" stroke-linecap="round"/>
  <path d="M123 200 205 118" stroke="#c75b39" stroke-width="20" stroke-linecap="round"/>
</svg>
'@
Set-Content -Path 'WebSite\public\favicon.svg' -Value $svg -Encoding UTF8
Set-Content -Path 'WebSite\public\images\brand\bettersurvival-logo.svg' -Value $svg -Encoding UTF8

'BS_LOGO_PNG_OK'
