[CmdletBinding()]
param(
	[ValidateRange(640, 16384)]
	[int] $Width = 2560,

	[ValidateRange(480, 16384)]
	[int] $Height = 1440,

	[ValidateRange(0, 60)]
	[int] $IntervalSeconds = 2,

	[switch] $DryRun
)

$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
	throw "PowerShell 7 or newer is required. Run this launcher through pwsh.exe."
}

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
$clientNames = @(
	'Player972',
	'PlayerB',
	'PlayerC',
	'PlayerD',
	'PlayerE'
)

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
	throw "Gradle Wrapper was not found: $gradleWrapper"
}

if (-not $DryRun) {
	Write-Host "Preparing Pixel TZZ Pro development clients..." -ForegroundColor Cyan
	& $gradleWrapper classes clientClasses --console=plain
	if ($LASTEXITCODE -ne 0) {
		throw "Client pre-compilation failed. Batch launch was cancelled."
	}
}

Write-Host (
	"Launching {0}; window {1}x{2}; interval {3} seconds." -f
	($clientNames -join " -> "),
	$Width,
	$Height,
	$IntervalSeconds
) -ForegroundColor Cyan

for ($index = 0; $index -lt $clientNames.Count; $index++) {
	$clientName = $clientNames[$index]
	$gradleArguments = @(
		'runClient',
		"-PpixelTzzUsername=$clientName",
		"-PpixelTzzWindowWidth=$Width",
		"-PpixelTzzWindowHeight=$Height",
		'--console=plain'
	)

	if ($DryRun) {
		Write-Host (
			"[{0}/{1}] {2} {3}" -f
			($index + 1),
			$clientNames.Count,
			$gradleWrapper,
			($gradleArguments -join ' ')
		) -ForegroundColor Yellow
		continue
	}

	$process = Start-Process `
		-FilePath $gradleWrapper `
		-ArgumentList $gradleArguments `
		-WorkingDirectory $repositoryRoot `
		-WindowStyle Hidden `
		-PassThru

	Write-Host (
		"[{0}/{1}] Started {2} (Gradle PID {3})" -f
		($index + 1),
		$clientNames.Count,
		$clientName,
		$process.Id
	) -ForegroundColor Green

	if ($index -lt ($clientNames.Count - 1) -and $IntervalSeconds -gt 0) {
		Start-Sleep -Seconds $IntervalSeconds
	}
}

if ($DryRun) {
	Write-Host "Dry run completed. No clients were started." -ForegroundColor Cyan
} else {
	Write-Host "All five client launch commands were issued. This window may now be closed." -ForegroundColor Cyan
}
