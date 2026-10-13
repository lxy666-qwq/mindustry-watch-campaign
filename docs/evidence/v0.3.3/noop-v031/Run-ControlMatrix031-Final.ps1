param(
    [string[]]$Names = @(),
    [string[]]$Modes = @('NORMAL','HEROIC','TACTICAL'),
    [int]$MaxSimSeconds = 1800,
    [string]$OutputDirectory = ('trial-work\control-matrix-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
$javaPath = 'coursework\工具\jdk-17.0.20.1+1\bin\java.exe'
$trialClasspath = 'trial-work\control031-final-classes;coursework\版本归档\v0.3.1\Mindustry.jar'
if($Names.Count -eq 0){
    $Names = @(
        'groundZero','saltFlats','testingGrounds','frozenForest','biomassFacility','taintedWoods',
        'crateredBattleground','ruinousShores','perilousHarbor','facility32m','windsweptIslands',
        'stainedMountains','extractionOutpost','coastline','weatheredChannels','navalFortress',
        'frontier','fungalPass','infestedCanyons','atolls','sunkenPier','mycelialBastion','overgrowth',
        'tarFields','impact0078','desolateRift','nuclearComplex','littoralShipyard','planetaryTerminal',
        'onset','aegis','lake','intersect','atlas','split','basin','marsh','peaks','ravine',
        'caldera-erekir','stronghold','crevice','siege','crossroads','karst','origin',
        'watch-1','watch-2','watch-3','watch-4','watch-5','watch-6','watch-7','watch-8','watch-9','watch-10'
    )
}
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
if(-not $resolvedOutput.StartsWith('trial-work\', [StringComparison]::OrdinalIgnoreCase)){
    throw 'Trial output must stay in trial-work.'
}
if(Test-Path -LiteralPath $resolvedOutput){ throw "A new output directory is required: $resolvedOutput" }
New-Item -ItemType Directory -Path $resolvedOutput | Out-Null
$rows = [Collections.Generic.List[object]]::new()
foreach($missionName in $Names){
    foreach($trialMode in $Modes){
        $caseDirectory = Join-Path $resolvedOutput ($missionName + '-' + $trialMode)
        New-Item -ItemType Directory -Path $caseDirectory | Out-Null
        Write-Host "$missionName $trialMode : native no-operation control, upper limit $MaxSimSeconds game seconds"
        & $javaPath -cp $trialClasspath LegalWatchTrial control $caseDirectory $trialMode $missionName $MaxSimSeconds *> (Join-Path $caseDirectory 'process.log')
        $processExit = $LASTEXITCODE
        $resultPath = Join-Path $caseDirectory 'result.txt'
        if(-not (Test-Path -LiteralPath $resultPath)){
            $rows.Add([pscustomobject]@{mission=$missionName;mode=$trialMode;finished='HARNESS_ERROR';winner='';simSeconds='';wave='';exitCode=$processExit;result=$resultPath})
        }else{
            $details = @{}
            foreach($line in (Get-Content -LiteralPath $resultPath -Encoding utf8)){
                if($line -match '^([A-Za-z][A-Za-z0-9_.]*)=(.*)$'){$details[$Matches[1]]=$Matches[2]}
            }
            $rows.Add([pscustomobject]@{mission=$missionName;mode=$trialMode;finished=$details.finished;winner=$details.winner;simSeconds=$details.simSeconds;wave=$details.wave;exitCode=$processExit;result=$resultPath})
        }
        $rows | Export-Csv -LiteralPath (Join-Path $resolvedOutput 'matrix.csv') -NoTypeInformation -Encoding utf8
    }
}
Write-Host "No-operation controls recorded at $resolvedOutput. These controls do not establish complete campaign playthroughs."
