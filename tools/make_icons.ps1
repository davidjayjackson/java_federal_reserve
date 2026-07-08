# Generate the two Extension Manager icons for the FRED Calc add-in.
# 42x42 PNG: line-chart motif (economic time-series) + "FRED" wordmark.
# Writes registration/icons/icon.png (default) and icon_hc.png (high-contrast).
#
# Usage:  pwsh -File tools/make_icons.ps1
Add-Type -AssemblyName System.Drawing

# Repo-root/registration/icons, resolved relative to this script (tools/).
$dest = Join-Path (Split-Path -Parent $PSScriptRoot) 'registration\icons'
if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }

$size = 42

function New-RoundedPath([int]$x, [int]$y, [int]$w, [int]$h, [int]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# Chart data points (upward-trending time series), in a 0..1 box.
$pts = @(
    @(0.08, 0.78),
    @(0.31, 0.55),
    @(0.54, 0.64),
    @(0.77, 0.30),
    @(1.00, 0.10)
)

function Draw-Icon($bgColor, $lineColor, $axisColor, $dotColor, $textColor, $path) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    # Background rounded rect
    $round = New-RoundedPath 1 1 ($size - 2) ($size - 2) 8
    if ($bgColor) {
        $brush = New-Object System.Drawing.SolidBrush($bgColor)
        $g.FillPath($brush, $round)
        $brush.Dispose()
    }

    # --- Chart occupies the top region; wordmark sits below -----------------
    $left = 8.0; $right = $size - 6.0
    $top  = 5.0; $bottom = 25.0
    $plotW = $right - $left
    $plotH = $bottom - $top

    # Axes (L-shape)
    $axisPen = New-Object System.Drawing.Pen($axisColor, 1.4)
    $g.DrawLine($axisPen, [single]$left, [single]$top, [single]$left, [single]$bottom)
    $g.DrawLine($axisPen, [single]$left, [single]$bottom, [single]$right, [single]$bottom)
    $axisPen.Dispose()

    # Chart line
    $screen = @()
    foreach ($pt in $pts) {
        $sx = $left + $pt[0] * $plotW
        $sy = $top + $pt[1] * $plotH
        $screen += (New-Object System.Drawing.PointF([single]$sx, [single]$sy))
    }
    $linePen = New-Object System.Drawing.Pen($lineColor, 2.6)
    $linePen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $linePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $linePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawLines($linePen, [System.Drawing.PointF[]]$screen)
    $linePen.Dispose()

    # Data dots
    $dotBrush = New-Object System.Drawing.SolidBrush($dotColor)
    foreach ($p in $screen) {
        $r = 1.9
        $g.FillEllipse($dotBrush, [single]($p.X - $r), [single]($p.Y - $r), [single]($r * 2), [single]($r * 2))
    }
    $dotBrush.Dispose()

    # --- "FRED" wordmark across the bottom ----------------------------------
    $font = New-Object System.Drawing.Font('Arial', 10.5, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textBrush = New-Object System.Drawing.SolidBrush($textColor)
    $rect = New-Object System.Drawing.RectangleF(0, 27, $size, 13)
    $g.DrawString('FRED', $font, $textBrush, $rect, $fmt)
    $textBrush.Dispose(); $font.Dispose(); $fmt.Dispose()

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# --- Default icon: bright blue background, gold chart, white wordmark ------
$blue  = [System.Drawing.Color]::FromArgb(255, 33, 118, 214)  # #2176D6 (bright)
$gold  = [System.Drawing.Color]::FromArgb(255, 255, 200, 64)  # #FFC840
$axisL = [System.Drawing.Color]::FromArgb(220, 225, 236, 250)
$dotW  = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$textW = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
Draw-Icon $blue $gold $axisL $dotW $textW (Join-Path $dest 'icon.png')

# --- High-contrast icon: black bg, pure white strokes (legible on any theme)
$black = [System.Drawing.Color]::FromArgb(255, 0, 0, 0)
$white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
Draw-Icon $black $white $white $white $white (Join-Path $dest 'icon_hc.png')

Write-Host "Wrote icons to $dest"
Get-ChildItem $dest | ForEach-Object { Write-Host ("  {0}  {1} bytes" -f $_.Name, $_.Length) }
