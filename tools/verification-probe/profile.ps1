# The verification probe's one profile: what it instruments, its own package and its log tag.
function Get-VerificationProbeProfile {
    return [pscustomobject]@{ Name = 'tiktok-whole-app-v1'; Package = 'com.zhiliaoapp.musically'; ProbePackage = 'app.adrmodtt.verification'
        Instrumentation = 'app.adrmodtt.verification.Probe'; LogTag = 'AdrmodttProbe' }
}
