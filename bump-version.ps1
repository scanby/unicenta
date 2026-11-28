# Incrementa la version de compilacion en todos los sitios.
# Uso: .\bump-version.ps1 3.82.2
param(
    [Parameter(Mandatory=$true)]
    [string]$Version
)

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Formato de version invalido: $Version (use X.Y.Z)"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$files = @(
    @{ Path = "src-pos\com\openbravo\pos\forms\AppLocal.java"; Pattern = 'public static final String APP_BUILD = "[^"]+";'; Replace = "public static final String APP_BUILD = `"$Version`";" },
    @{ Path = "build_working.xml"; Pattern = '<property name="version" value="[^"]+"/>'; Replace = "<property name=`"version`" value=`"$Version`"/>" },
    @{ Path = "installer\unicentaopos.iss"; Pattern = '#define MyAppVersion "[^"]+"'; Replace = "#define MyAppVersion `"$Version`"" }
)

foreach ($item in $files) {
    $full = Join-Path $root $item.Path
    if (-not (Test-Path $full)) { throw "No encontrado: $($item.Path)" }
    $text = [System.IO.File]::ReadAllText($full)
    if (-not [regex]::IsMatch($text, $item.Pattern)) { throw "No se encontro el patron en $($item.Path)" }
    $text = [regex]::Replace($text, $item.Pattern, $item.Replace)
    [System.IO.File]::WriteAllText($full, $text)
    "Actualizado: $($item.Path)"
}

""
"Version de compilacion: $Version"
"Version de esquema BD: sin cambios (AppLocal.APP_VERSION)"
""
"Siguiente paso: ant -f build.xml release"
"Luego: git tag -a v$Version -m 'Release $Version' ; git push origin v$Version"
