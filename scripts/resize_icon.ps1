Add-Type -AssemblyName System.Drawing
$src = "C:\Users\camip\Desktop\ocdev\DuoVial\assets\icon.png"
$img = [System.Drawing.Image]::FromFile($src)

$dpi = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

$root = "C:\Users\camip\Desktop\ocdev\DuoVial\kmp\composeApp\src\androidMain\res"
foreach ($k in $dpi.Keys) {
    $size = $dpi[$k]
    $dir = Join-Path $root $k
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    foreach ($name in @("ic_launcher.png", "ic_launcher_round.png")) {
        $bmp = New-Object System.Drawing.Bitmap $size, $size
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.DrawImage($img, 0, 0, $size, $size)
        $g.Dispose()
        $bmp.Save((Join-Path $dir $name), [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    }
}

# Foreground adaptive icon (108dp = 432px @xxxhdpi), use xxhdpi (324px) which is safe zone
$fgsize = 324
$fg = New-Object System.Drawing.Bitmap $fgsize, $fgsize
$fgG = [System.Drawing.Graphics]::FromImage($fg)
$fgG.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$fgG.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$fgG.DrawImage($img, 0, 0, $fgsize, $fgsize)
$fgG.Dispose()
$fgPath = "C:\Users\camip\Desktop\ocdev\DuoVial\kmp\composeApp\src\androidMain\res\drawable-nodpi\ic_launcher_foreground.png"
$fg.Save($fgPath, [System.Drawing.Imaging.ImageFormat]::Png)
$fg.Dispose()
$img.Dispose()
Write-Host "Done"
