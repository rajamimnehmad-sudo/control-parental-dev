package reference.autopilot

data class ActionToken(val windowId:Int,val generation:Long,val fingerprint:String)

class AutopilotActionGate {
    fun authorize(
        token: ActionToken,
        currentToken: ActionToken,
        target: MatchedTarget?,
        expectedKey: TargetKey
    ): Boolean {
        if (token != currentToken) return false
        if (target == null || target.key != expectedKey) return false
        if (target.confidence != Confidence.HIGH) return false
        if (!target.clickable || !target.unique || !target.marginOk) return false
        return true
    }
}
