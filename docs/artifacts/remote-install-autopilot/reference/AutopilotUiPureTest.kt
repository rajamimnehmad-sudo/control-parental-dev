package reference.autopilot

private fun node(path:String,text:String,click:Boolean=true,checkable:Boolean=false,checked:Boolean?=null,id:String?=null, ancestors:List<String> = emptyList()) =
    UiNodeSnapshot(path,text=text,clickable=click,checkable=checkable,checked=checked,viewId=id,ancestorTexts=ancestors,bounds=IntRect(0,0,100,40))

fun main() {
    var n=0
    fun ok(v:Boolean,msg:String=""){ check(v){msg}; n++ }
    val selector=SettingsWindowSelector()
    val overlay=UiWindowSnapshot(2,"com.example.glosh",WindowKind.ACCESSIBILITY_OVERLAY,true,listOf(node("o","Número de compilación")))
    val ime=UiWindowSnapshot(3,"com.samsung.android.honeyboard",WindowKind.INPUT_METHOD,true,listOf(node("i","123456")))
    val settings=UiWindowSnapshot(1,"com.android.settings",WindowKind.APPLICATION,false,listOf(node("s","Información de software")))
    ok(selector.select(listOf(overlay,ime,settings)) is WindowSelection.Selected)
    ok(selector.select(listOf(overlay,ime)) is WindowSelection.Rejected)
    ok(selector.select(listOf(settings,settings.copy(id=4))) is WindowSelection.Rejected)

    fun trusted(nodes:List<UiNodeSnapshot>):TrustedSettingsSnapshot {
        val w=UiWindowSnapshot(1,"com.android.settings",WindowKind.APPLICATION,true,nodes)
        return (selector.select(listOf(w)) as WindowSelection.Selected).snapshot
    }
    val c=SamsungSettingsClassifier()

    val about=trusted(listOf(node("toolbar","Acerca del teléfono",false),node("sw","Información de software",true)))
    val ca=c.classify(about)
    ok(ca.screen==Screen.ABOUT_PHONE)
    ok(ca.targets[TargetKey.SOFTWARE_INFO]?.confidence==Confidence.HIGH)

    val sw=trusted(listOf(
        node("toolbar","Información de software",false),
        node("build","Número de compilación",true,id="com.android.settings:id/title",ancestors=listOf("Información de software"))
    ))
    val cs=c.classify(sw)
    ok(cs.screen==Screen.SOFTWARE_INFO)
    ok(cs.targets[TargetKey.BUILD_NUMBER]?.confidence==Confidence.HIGH)

    val dev=trusted(listOf(node("toolbar","Opciones de desarrollador",false),node("wire","Depuración inalámbrica",true)))
    val cd=c.classify(dev)
    ok(cd.screen==Screen.DEVELOPER_OPTIONS)
    ok(cd.targets[TargetKey.WIRELESS_DEBUGGING]?.confidence==Confidence.HIGH)

    val wireless=trusted(listOf(
        node("toolbar","Depuración inalámbrica",false),
        node("toggle","Depuración inalámbrica",true,checkable=true,checked=false),
        node("pair","Vincular dispositivo con código de vinculación",true)
    ))
    val cw=c.classify(wireless)
    ok(cw.screen==Screen.WIRELESS_DEBUGGING)
    ok(cw.targets[TargetKey.PAIR_WITH_CODE]?.confidence==Confidence.HIGH)

    val duplicate=trusted(listOf(node("toolbar","Acerca del teléfono",false),node("a","Información de software",true),node("b","Información de software",true)))
    val dup=c.classify(duplicate).targets[TargetKey.SOFTWARE_INFO]
    ok(dup?.confidence!=Confidence.HIGH)

    val detector=PairingCodeDetector()
    val pairSnap=trusted(listOf(node("toolbar","Depuración inalámbrica",false),node("ctx","Código de vinculación",false),node("code","123456",false),node("pair","Vincular dispositivo con código",true)))
    val pairClass=c.classify(pairSnap)
    ok(detector.detect(pairSnap,pairClass)==PairingCodeResult.Unique("123456"))
    val ambiguous=trusted(listOf(node("toolbar","Depuración inalámbrica",false),node("ctx","Código de vinculación",false),node("a","123456",false),node("b","654321",false),node("pair","Vincular dispositivo con código",true)))
    ok(detector.detect(ambiguous,c.classify(ambiguous)) is PairingCodeResult.Rejected)

    val gate=AutopilotActionGate()
    val t=ActionToken(1,7,sw.fingerprint)
    val target=cs.targets[TargetKey.BUILD_NUMBER]
    ok(gate.authorize(t,t,target,TargetKey.BUILD_NUMBER))
    ok(!gate.authorize(t,t.copy(generation=8),target,TargetKey.BUILD_NUMBER))
    ok(!gate.authorize(t,t,target,TargetKey.SOFTWARE_INFO))

    val unknown=trusted(listOf(node("x","Cualquier cosa",true)))
    ok(c.classify(unknown).screen==Screen.UNKNOWN)

    println("PASS $n pure Kotlin UI checks")
}
