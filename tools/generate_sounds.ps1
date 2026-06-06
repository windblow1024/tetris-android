# Generate simple WAV sound effects for Tetris
# Each sound is PCM 16-bit mono at 22050 Hz

$outDir = "$PSScriptRoot\..\app\src\main\res\raw"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$SAMPLE_RATE = 22050

function New-Wav {
    param($Path, $Samples)
    $count = $Samples.Count
    $dataSize = $count * 2
    $fileSize = 36 + $dataSize

    $stream = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($stream)
    
    # RIFF header
    $writer.Write([char[]]'RIFF')
    $writer.Write([int]$fileSize)
    $writer.Write([char[]]'WAVE')
    
    # fmt chunk
    $writer.Write([char[]]'fmt ')
    $writer.Write([int]16)              # subchunk size
    $writer.Write([System.Int16]1)             # PCM
    $writer.Write([System.Int16]1)             # mono
    $writer.Write([int]$SAMPLE_RATE)    # sample rate
    $writer.Write([int]($SAMPLE_RATE * 2))  # byte rate
    $writer.Write([System.Int16]2)             # block align
    $writer.Write([System.Int16]16)            # bits per sample
    
    # data chunk
    $writer.Write([char[]]'data')
    $writer.Write([int]$dataSize)
    
    # write samples as 16-bit signed little-endian
    foreach ($s in $Samples) {
        $clamped = [Math]::Max(-32768, [Math]::Min(32767, [int]$s))
        $writer.Write([System.Int16]$clamped)
    }
    
    $writer.Flush()
    [IO.File]::WriteAllBytes($Path, $stream.ToArray())
    $writer.Dispose()
    $stream.Dispose()
}

function Get-Envelope {
    param($Len, $Attack = 0.05, $Release = 0.15)
    $env = [float[]]::new($Len)
    $aEnd = [int]($Len * $Attack)
    $rStart = [int]($Len * (1 - $Release))
    
    for ($i = 0; $i -lt $Len; $i++) {
        if ($i -lt $aEnd) {
            $env[$i] = [float]$i / $aEnd
        } elseif ($i -ge $rStart) {
            $env[$i] = 1.0 - ([float]($i - $rStart) / ($Len - $rStart))
        } else {
            $env[$i] = 1.0
        }
    }
    return $env
}

function Get-SineSweep {
    param($StartFreq, $EndFreq, $DurationMs, $Volume = 0.5)
    $len = [int]($SAMPLE_RATE * $DurationMs / 1000)
    $samples = [float[]]::new($len)
    $env = Get-Envelope $len -Attack 0.03 -Release 0.2
    for ($i = 0; $i -lt $len; $i++) {
        $t = [float]$i / $SAMPLE_RATE
        $freq = $StartFreq + ($EndFreq - $StartFreq) * ($i / $len)
        $phase = 2 * [Math]::PI * $t * $freq
        $samples[$i] = [Math]::Sin($phase) * $Volume * 32767 * $env[$i]
    }
    return $samples
}

function Get-SineTone {
    param($Freq, $DurationMs, $Volume = 0.5, $Attack = 0.03, $Release = 0.2)
    $len = [int]($SAMPLE_RATE * $DurationMs / 1000)
    $samples = [float[]]::new($len)
    $env = Get-Envelope $len -Attack $Attack -Release $Release
    for ($i = 0; $i -lt $len; $i++) {
        $t = [float]$i / $SAMPLE_RATE
        $samples[$i] = [Math]::Sin(2 * [Math]::PI * $t * $Freq) * $Volume * 32767 * $env[$i]
    }
    return $samples
}

function Get-SquareTone {
    param($Freq, $DurationMs, $Volume = 0.3)
    $len = [int]($SAMPLE_RATE * $DurationMs / 1000)
    $samples = [float[]]::new($len)
    $env = Get-Envelope $len -Attack 0.02 -Release 0.15
    for ($i = 0; $i -lt $len; $i++) {
        $t = [float]$i / $SAMPLE_RATE
        $samples[$i] = [Math]::Sign([Math]::Sin(2 * [Math]::PI * $t * $Freq)) * $Volume * 32767 * $env[$i]
    }
    return $samples
}

function Get-TriangleTone {
    param($Freq, $DurationMs, $Volume = 0.4)
    $len = [int]($SAMPLE_RATE * $DurationMs / 1000)
    $samples = [float[]]::new($len)
    $env = Get-Envelope $len -Attack 0.02 -Release 0.15
    for ($i = 0; $i -lt $len; $i++) {
        $t = [float]$i / $SAMPLE_RATE
        $phase = ($t * $Freq) % 1.0
        $val = if ($phase -lt 0.5) { 4 * $phase - 1 } else { 3 - 4 * $phase }
        $samples[$i] = $val * $Volume * 32767 * $env[$i]
    }
    return $samples
}

function Concat-Sounds {
    param($SoundArrays)
    $totalLen = ($SoundArrays | ForEach-Object { $_.Count }) | Measure-Object -Sum | Select-Object -ExpandProperty Sum
    $result = [float[]]::new($totalLen)
    $offset = 0
    foreach ($arr in $SoundArrays) {
        for ($i = 0; $i -lt $arr.Count; $i++) {
            $result[$offset + $i] = $arr[$i]
        }
        $offset += $arr.Count
    }
    return $result
}

Write-Host "Generating sounds..."

# 1. move.wav — short click
$s = Get-TriangleTone -Freq 200 -DurationMs 40 -Volume 0.3
New-Wav -Path "$outDir\move.wav" -Samples $s
Write-Host "  move.wav"

# 2. rotate.wav — quick swish up
$s = Get-SineSweep -StartFreq 400 -EndFreq 700 -DurationMs 70 -Volume 0.35
New-Wav -Path "$outDir\rotate.wav" -Samples $s
Write-Host "  rotate.wav"

# 3. softdrop.wav — light tap
$s = Get-SineTone -Freq 180 -DurationMs 30 -Volume 0.25 -Attack 0.01 -Release 0.1
New-Wav -Path "$outDir\softdrop.wav" -Samples $s
Write-Host "  softdrop.wav"

# 4. harddrop.wav — thump
$s = Get-SineSweep -StartFreq 120 -EndFreq 60 -DurationMs 100 -Volume 0.5
New-Wav -Path "$outDir\harddrop.wav" -Samples $s
Write-Host "  harddrop.wav"

# 5. lock.wav — solid thud  
$s = Get-SquareTone -Freq 100 -DurationMs 50 -Volume 0.25
New-Wav -Path "$outDir\lock.wav" -Samples $s
Write-Host "  lock.wav"

# 6. clear.wav — bright chime
$s = Get-SineTone -Freq 523 -DurationMs 120 -Volume 0.35 -Attack 0.02 -Release 0.3
New-Wav -Path "$outDir\clear.wav" -Samples $s
Write-Host "  clear.wav"

# 7. tetris.wav — C-E-G-C arpeggio (overlapping)
$c4 = Get-SineTone -Freq 523 -DurationMs 70 -Volume 0.3 -Attack 0.02 -Release 0.4
$e4 = Get-SineTone -Freq 659 -DurationMs 70 -Volume 0.3 -Attack 0.02 -Release 0.4
$g4 = Get-SineTone -Freq 784 -DurationMs 70 -Volume 0.3 -Attack 0.02 -Release 0.4
$c5 = Get-SineTone -Freq 1047 -DurationMs 150 -Volume 0.4 -Attack 0.02 -Release 0.5
# Concatenate with overlap - pad each with silence, sum
$padLen = [int]($SAMPLE_RATE * 30 / 1000)  # 30ms padding
$pad = [float[]]::new($padLen)
$part1 = $c4 + $pad
$part2 = $pad + $e4 + $pad
$part3 = $pad + $pad + $g4
$part4 = $pad + $pad + $pad + $c5
$maxLen = @($part1.Count, $part2.Count, $part3.Count, $part4.Count) | Measure-Object -Maximum | Select-Object -ExpandProperty Maximum
$result = [float[]]::new($maxLen)
for ($i = 0; $i -lt $part1.Count; $i++) { $result[$i] += $part1[$i] }
for ($i = 0; $i -lt $part2.Count; $i++) { $result[$i] += $part2[$i] }
for ($i = 0; $i -lt $part3.Count; $i++) { $result[$i] += $part3[$i] }
for ($i = 0; $i -lt $part4.Count; $i++) { $result[$i] += $part4[$i] }
$max = [Math]::Max(1, ($result | ForEach-Object { [Math]::Abs($_) } | Measure-Object -Maximum).Maximum)
for ($i = 0; $i -lt $maxLen; $i++) { $result[$i] = $result[$i] / $max * 32767 }
New-Wav -Path "$outDir\tetris.wav" -Samples $result
Write-Host "  tetris.wav"

# 8. levelup.wav — ascending scale (consecutive)
$notes = @(523, 587, 659, 698, 784)  # C D E F G
$parts = @()
foreach ($f in $notes) {
    $parts += ,(Get-SineTone -Freq $f -DurationMs 60 -Volume 0.3 -Attack 0.02 -Release 0.35)
}
$s = Concat-Sounds $parts
New-Wav -Path "$outDir\levelup.wav" -Samples $s
Write-Host "  levelup.wav"

# 9. gameover.wav — descending sad notes (consecutive)
$notes = @(392, 349, 330, 294, 262)  # G4 F4 E4 D4 C4
$parts = @()
foreach ($f in $notes) {
    $parts += ,(Get-SineTone -Freq $f -DurationMs 100 -Volume 0.35 -Attack 0.03 -Release 0.4)
}
$s = Concat-Sounds $parts
New-Wav -Path "$outDir\gameover.wav" -Samples $s
Write-Host "  gameover.wav"

# 10. hold.wav — soft click
$s = Get-TriangleTone -Freq 300 -DurationMs 40 -Volume 0.25
New-Wav -Path "$outDir\hold.wav" -Samples $s
Write-Host "  hold.wav"

# 11. levelstart.wav — short fanfare
$c4 = Get-SineTone -Freq 523 -DurationMs 60 -Volume 0.3 -Attack 0.02 -Release 0.25
$e4 = Get-SineTone -Freq 659 -DurationMs 60 -Volume 0.3 -Attack 0.02 -Release 0.25
$g4 = Get-SineTone -Freq 784 -DurationMs 100 -Volume 0.35 -Attack 0.02 -Release 0.3
$s = Concat-Sounds @(,$c4, $e4, $g4)
New-Wav -Path "$outDir\levelstart.wav" -Samples $s
Write-Host "  levelstart.wav"

Write-Host "Done! Generated 11 WAV files in $outDir"
Get-ChildItem $outDir -Filter "*.wav" | ForEach-Object { Write-Host "  $($_.Name) ($($_.Length) bytes)" }
