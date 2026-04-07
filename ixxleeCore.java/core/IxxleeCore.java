javac ixxleeCore.java
java IxxleeCore

package com.ixxlee.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IxxleeCore {

    public static void addJarToClasspath(String jarPath) {
        try {
            File file = new File(jarPath);
            if (!file.exists()) {
                throw new RuntimeException("JAR not found: " + jarPath);
            }
            URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class<?> clazz = URLClassLoader.class;
            java.lang.reflect.Method method = clazz.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(sysLoader, file.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Cannot add JAR to classpath", e);
        }
    }

    public static void initRuntimeJars() {
        addJarToClasspath("libs/kotlin-stdlib.jar");
        addJarToClasspath("libs/your-runtime-dependency.jar");
    }

    public static void main(String[] args) {
        File jar = new File("app-all.jar");
        if (!jar.exists()) {
            System.out.println("app-all.jar not found in " + jar.getAbsoluteFile().getParent());
            return;
        }
        try {
            Process process = new ProcessBuilder("java", "-jar", jar.getAbsolutePath())
                    .directory(jar.getParentFile() != null ? jar.getParentFile() : new File("."))
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            System.out.println("app-all.jar exited with code " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runAndroidUnlockGenerator(BufferedReader reader) {
        String bypassKey = prompt(reader, "Enter Android bypass key");
        if (bypassKey == null || bypassKey.isBlank()) {
            System.out.println("Invalid bypass key.");
            return;
        }
        String code = generateAndroidUnlockCode(bypassKey.trim());
        System.out.println("Android unlock code: " + code);
    }

    private static void runIosUnlockGenerator(BufferedReader reader) {
        String bypassKey = prompt(reader, "Enter iOS bypass key");
        if (bypassKey == null || bypassKey.isBlank()) {
            System.out.println("Invalid bypass key.");
            return;
        }
        String code = generateIosUnlockCode(bypassKey.trim());
        System.out.println("iOS unlock code: " + code);
    }

    private static void runDeveloperMode(BufferedReader reader) {
        String input = prompt(reader, "Enter developer passcode");
        if (input == null) {
            System.out.println("No passcode entered.");
            return;
        }
        if (DevModeManager.unlock(input.trim())) {
            System.out.println("Developer mode enabled.");
            if (DevModeManager.isEnabled()) {
                System.out.println("Dev mode is active. You may now access hidden features.");
            }
        } else {
            System.out.println("Wrong developer passcode.");
        }
    }

    private static String generateAndroidUnlockCode(String bypassKey) {
        String hash = hashString(bypassKey + ":ANDROID");
        return formatCode("AND-" + hash.substring(0, 4) + "-" + hash.substring(4, 8) + "-" + hash.substring(8, 12));
    }

    private static String generateIosUnlockCode(String bypassKey) {
        String hash = hashString(bypassKey + ":IOS");
        return formatCode("IOS-" + hash.substring(0, 4) + "-" + hash.substring(4, 8) + "-" + hash.substring(8, 12));
    }

    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02X", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String formatCode(String code) {
        return code.replaceAll("[^A-Z0-9-]", "");
    }
}

// ---------------------------------------------------------
// Developer Mode
// ---------------------------------------------------------

class DevModeManager {
    private static final String PASSCODE = "006660";
    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean unlock(String input) {
        boolean ok = input.equals(PASSCODE);
        if (ok) enabled = true;
        return ok;
    }
}

# Configuration
$TrackerFile = Join-Path $BucketPath ".github/data/app-tracker.json"
$ManifestsPath = Join-Path $BucketPath "bucket"

Write-Host "##[group]🚀 Smart Updater Start" -ForegroundColor Blue
Write-Host "Bucket Path: $BucketPath" -ForegroundColor Cyan
Write-Host "Max Apps Per Run: $MaxAppsPerRun" -ForegroundColor Cyan
Write-Host "Check Interval: $CheckIntervalHours hours" -ForegroundColor Cyan

# Load tracker database
if (Test-Path $TrackerFile) {
    $trackerContent = Get-Content $TrackerFile -Raw
    if ($trackerContent.Trim() -ne "") {
        try {
            $tracker = $trackerContent | ConvertFrom-Json -AsHashtable
            Write-Host "Loaded tracker with $($tracker.Count) apps" -ForegroundColor Green
        } catch {
            Write-Host "##[warning]Failed to parse tracker, starting fresh" -ForegroundColor Yellow
            $tracker = @{}
        }
    } else {
        $tracker = @{}
        Write-Host "Tracker file empty, starting fresh" -ForegroundColor Yellow
    }
} else {
    $tracker = @{}
    Write-Host "No tracker file found, starting fresh" -ForegroundColor Yellow
}

# Get all manifests
$manifests = Get-ChildItem $ManifestsPath -Filter *.json
$summary.TotalManifests = $manifests.Count

Write-Host "Found $($manifests.Count) manifests" -ForegroundColor Cyan
Write-Host "##[endgroup]" -ForegroundColor Blue

# Calculate which apps to check
$appsToCheck = @()
$now = Get-Date

foreach ($manifest in $manifests) {
    $appName = $manifest.BaseName
    
    # Check if app is in tracker
    if ($tracker.ContainsKey($appName)) {
        try {
            $lastChecked = [datetime]::Parse($tracker[$appName].lastChecked)
            $hoursSinceCheck = ($now - $lastChecked).TotalHours
            
            # Skip if checked recently
            if ($hoursSinceCheck -lt $CheckIntervalHours) {
                continue
            }
            
            # Calculate priority score
            $updateFrequency = $tracker[$appName].updateFrequency
            $daysSinceUpdate = if ($tracker[$appName].lastUpdated) {
                ($now - [datetime]::Parse($tracker[$appName].lastUpdated)).TotalDays
            } else {
                365  # Never updated, high priority
            }
            
            $priorityScore = $daysSinceUpdate * $updateFrequency
            
            $appsToCheck += [PSCustomObject]@{
                Name = $appName
                Path = $manifest.FullName
                Priority = $priorityScore
                LastChecked = $lastChecked
                IsNew = $false
            }
        } catch {
            # Invalid tracker entry, treat as new
            $summary.Warnings += "Invalid tracker entry for $appName, resetting"
            $appsToCheck += [PSCustomObject]@{
                Name = $appName
                Path = $manifest.FullName
                Priority = 500
                LastChecked = $null
                IsNew = $true
            }
        }
    } else {
        # New app, high priority
        $appsToCheck += [PSCustomObject]@{
            Name = $appName
            Path = $manifest.FullName
            Priority = 1000
            LastChecked = $null
            IsNew = $true
        }
    }
}

$summary.AppsToCheck = $appsToCheck.Count

# Sort by priority (highest first) and take top N
$appsToCheck = $appsToCheck | Sort-Object Priority -Descending | Select-Object -First $MaxAppsPerRun

Write-Host "##[group]📋 Apps Selected for Check" -ForegroundColor Blue
Write-Host "Checking $($appsToCheck.Count) apps this run:" -ForegroundColor Green
$appsToCheck | ForEach-Object { 
    $status = if ($_.IsNew) { "NEW" } else { "EXISTING" }
    Write-Host "  - $($_.Name) (priority: $($_.Priority), status: $status)" 
}
Write-Host "##[endgroup]" -ForegroundColor Blue

# Setup Scoop
if (-not (Get-Command scoop -ErrorAction SilentlyContinue)) {
    Write-Host "##[group]⬇️ Installing Scoop" -ForegroundColor Blue
    try {
        Invoke-RestMethod get.scoop.sh | Invoke-Expression
        Write-Host "Scoop installed successfully" -ForegroundColor Green
    } catch {
        Write-Host "##[error]Failed to install Scoop: $_" -ForegroundColor Red
        $summary.Errors += "Failed to install Scoop: $_"
        exit 1
    }
    Write-Host "##[endgroup]" -ForegroundColor Blue
}

# Add local bucket (using absolute path)
$bucketFullPath = Resolve-Path $BucketPath
Write-Host "##[group]📂 Adding Local Bucket" -ForegroundColor Blue
Write-Host "Bucket path: $bucketFullPath" -ForegroundColor Cyan

try {
    $bucketOutput = scoop.cmd bucket add local "$bucketFullPath" 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Bucket added successfully" -ForegroundColor Green
    } else {
        Write-Host "##[warning]Bucket add returned non-zero exit code: $LASTEXITCODE" -ForegroundColor Yellow
        Write-Host "Output: $bucketOutput" -ForegroundColor Gray
    }
} catch {
    Write-Host "##[warning]Error adding bucket: $_" -ForegroundColor Yellow
}
Write-Host "##[endgroup]" -ForegroundColor Blue

# Check each app
$checkedThisRun = @{}
Write-Host "##[group]🔍 Checking Apps" -ForegroundColor Blue

foreach ($app in $appsToCheck) {
    if ($checkedThisRun.ContainsKey($app.Name)) {
        Write-Host "  ⏭️  Skipping $($app.Name) - already checked" -ForegroundColor Gray
        continue
    }
    
    Write-Host "  🔍 Checking $($app.Name)..." -ForegroundColor Cyan
    
    try {
        # Run scoop checkver
        $result = scoop.cmd checkver $app.Name --force 2>&1 | Out-String
        $summary.AppsChecked++
        
        # Check if update is available
        if ($result -match "(\S+)\s+->\s+(\S+)") {
            $oldVersion = $matches[1]
            $newVersion = $matches[2]
            
            Write-Host "    ✅ Update available: $oldVersion -> $newVersion" -ForegroundColor Green
            
            # Update tracker
            $tracker[$app.Name] = @{
                lastChecked = $now.ToString("o")
                lastUpdated = $now.ToString("o")
                currentVersion = $newVersion
                updateFrequency = 1.0
            }
            
            $summary.AppsUpdated++
            $summary.UpdatedApps += "$app.Name ($oldVersion → $newVersion)"
            
            # Add GitHub annotation for workflow
            Write-Host "##[group]📦 $($app.Name) Update Found" -ForegroundColor Green
            Write-Host "Old: $oldVersion" -ForegroundColor Gray
            Write-Host "New: $newVersion" -ForegroundColor Green
            Write-Host "##[endgroup]" -ForegroundColor Green
            
        } else {
            # No update found
            Write-Host "    ⏹️  No update available" -ForegroundColor Gray
            
            # Get current version from manifest
            $manifestContent = Get-Content $app.Path -Raw | ConvertFrom-Json
            $currentVersion = $manifestContent.version
            
            # Update tracker with last checked time
            if (-not $tracker.ContainsKey($app.Name)) {
                $tracker[$app.Name] = @{}
            }
            
            $tracker[$app.Name].lastChecked = $now.ToString("o")
            $tracker[$app.Name].currentVersion = $currentVersion
            
            # Adjust update frequency (reduce if no update)
            if ($tracker[$app.Name].updateFrequency) {
                $tracker[$app.Name].updateFrequency = [math]::Max(0.1, $tracker[$app.Name].updateFrequency * 0.95)
            } else {
                $tracker[$app.Name].updateFrequency = 0.5
            }
        }
    } catch {
        $errorMsg = "Error checking $($app.Name): $_"
        Write-Host "    ❌ $errorMsg" -ForegroundColor Red
        $summary.AppsWithErrors++
        $summary.Errors += $errorMsg
        
        # Still update tracker with error time
        if (-not $tracker.ContainsKey($app.Name)) {
            $tracker[$app.Name] = @{}
        }
        $tracker[$app.Name].lastChecked = $now.ToString("o")
        $tracker[$app.Name].updateFrequency = 0.1
    }
    
    $checkedThisRun[$app.Name] = $true
    
    # Small delay to avoid rate limits
    Start-Sleep -Milliseconds 300
}

Write-Host "##[endgroup]" -ForegroundColor Blue

# Save tracker
Write-Host "##[group]💾 Saving Tracker" -ForegroundColor Blue
try {
    $trackerJson = $tracker | ConvertTo-Json -Depth 3
    Set-Content -Path $TrackerFile -Value $trackerJson
    Write-Host "Tracker saved with $($tracker.Count) apps" -ForegroundColor Green
} catch {
    $errorMsg = "Failed to save tracker: $_"
    Write-Host "##[error]$errorMsg" -ForegroundColor Red
    $summary.Errors += $errorMsg
}
Write-Host "##[endgroup]" -ForegroundColor Blue

# Final summary
Write-Host "##[group]📊 Run Summary" -ForegroundColor Blue
Write-Host "Total Manifests: $($summary.TotalManifests)" -ForegroundColor Cyan
Write-Host "Apps Selected: $($summary.AppsToCheck)" -ForegroundColor Cyan
Write-Host "Apps Checked: $($summary.AppsChecked)" -ForegroundColor Cyan
Write-Host "Apps Updated: $($summary.AppsUpdated)" -ForegroundColor $(if ($summary.AppsUpdated -gt 0) { "Green" } else { "Gray" })
Write-Host "Apps with Errors: $($summary.AppsWithErrors)" -ForegroundColor $(if ($summary.AppsWithErrors -gt 0) { "Red" } else { "Gray" })

if ($summary.UpdatedApps.Count -gt 0) {
    Write-Host "`n✅ Updated Apps:" -ForegroundColor Green
    $summary.UpdatedApps | ForEach-Object { Write-Host "  - $_" -ForegroundColor Green }
}

if ($summary.Warnings.Count -gt 0) {
    Write-Host "`n⚠️ Warnings:" -ForegroundColor Yellow
    $summary.Warnings | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
}

if ($summary.Errors.Count -gt 0) {
    Write-Host "`n❌ Errors:" -ForegroundColor Red
    $summary.Errors | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
Write-Host "##[endgroup]" -ForegroundColor Blue

# Set workflow output
Write-Host "##[set-output name=apps_updated;]$($summary.AppsUpdated)"
Write-Host "##[set-output name=apps_checked;]$($summary.AppsChecked)"
Write-Host "##[set-output name=apps_with_errors;]$($summary.AppsWithErrors)"
Write-Host "##[set-output name=has_updates;]$($summary.AppsUpdated -gt 0)"

# Exit with appropriate code
if ($summary.AppsUpdated -gt 0) {
    Write-Host "##[group]🚀 Updates Found - Commit Required" -ForegroundColor Green
    Write-Host "Exit code: 100 (updates found)" -ForegroundColor Green
    Write-Host "##[endgroup]" -ForegroundColor Green
    exit 100
} else {
    Write-Host "##[group]✅ No Updates Found" -ForegroundColor Gray
    Write-Host "Exit code: 0 (no updates)" -ForegroundColor Gray
    Write-Host "##[endgroup]" -ForegroundColor Gray
    exit 0
}
