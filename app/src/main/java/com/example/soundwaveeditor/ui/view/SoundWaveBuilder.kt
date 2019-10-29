package com.example.soundwaveeditor.ui.view

import androidx.annotation.ColorRes
import androidx.annotation.Dimension


//class SWBuilder private constructor() {
//
//    fun test() {
//        val o = OptionalDick()
//
//        o.setDick("").setDick("anme", 23).setDick(1488).nexil(12)
//    }
//
//    fun create(): RequiredDick {
//
//    }
//}
//
//
//fun OptionalDick.setDick(name: String): NextDick {
//
//}
//
//fun NextDick.setDick(name: String, size: Int): NextDick {
//
//}
//
//fun NextDick.setDick(size: Int): NextDick {
//
//}
//
//fun NextDick.nexil(age: Int): RequiredDick {
//
//}
//
//class RequiredDick {
//
//}
//
//class NextDick {
//
//}
//
//class OptionalDick {
//
//}

class Builder private constructor() {

    fun create() = Req1()

    inner class Req1 {
        fun setNeedToPlayInSlideBars(needToPlayInSlideBars: Boolean) = Req2()
    }

    inner class Req2 {
        fun setNeedToShowTime(needToShowTime: Boolean) = Req3()
    }

    inner class Req3 {
        fun setMaxTrimLengthInSeconds(seconds: Int) = Req4()
    }

    inner class Req4 {
        fun setMinTrimLengthInSeconds(seconds: Int) = Req5()
    }

    inner class Req5 {
        fun setCallback(callback: () -> Unit) = OptionalParam()  // TODO define callback
    }

    inner class OptionalParam : Opti {

    }
}

interface OptionalBuilder {

    fun setHistogramBackground(@ColorRes color: Int): OptionalParam
    fun setActiveColumnsColor(@ColorRes color: Int): OptionalParam
    fun setInactiveColumnsColor(@ColorRes color: Int): OptionalParam
    fun setSlideBarsColor(@ColorRes color: Int): OptionalParam
    fun setPlayBarColor(@ColorRes color: Int): OptionalParam
    fun setTimeTextColor(@ColorRes color: Int): OptionalParam
    fun setTimeTextSize(@Dimension dimension: Float): OptionalParam
    fun setMaxVisibleColumnsCount(count: Int): OptionalParam
    fun setMinVisibleColumnsCount(count: Int): OptionalParam
    fun setSlideBarsPaddingInColumns(padding: Int): OptionalParam
    fun setColumnSpacingRatio(ratio: Float): OptionalParam
    fun setColumnVerticalPaddingRatio(ratio: Float): OptionalParam
    fun setHistogramTopPaddingRatio(ratio: Float): OptionalParam
    fun setNeedToRoundColumns(needToRoundColumns: Boolean): OptionalParam
}
