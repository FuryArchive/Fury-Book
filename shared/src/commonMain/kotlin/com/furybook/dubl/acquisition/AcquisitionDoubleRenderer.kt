package com.furybook.dubl.acquisition

/** Acquisition-only exact binary64 rendering. Legacy JSON writers deliberately keep their policy. */
internal fun acquisitionDoubleText(value: Double): String {
    val bits=value.toRawBits()
    val exponent=((bits ushr 52) and 2047).toInt()
    require(exponent!=2047) { "Acquisition Double must be finite" }
    val negative=bits<0
    val fraction=bits and 0x000fffffffffffffL
    if(exponent==0 && fraction==0L) return if(negative) "-0.0" else "0.0"
    val significand=if(exponent==0) fraction else fraction or (1L shl 52)
    val binaryExponent=if(exponent==0) -1074 else exponent-1023-52
    // Decimal digits need only small exact Int arithmetic (maximum product 9*5+4).
    var digits=significand.toString()
    fun multiply(factor:Int) {
        val result=CharArray(digits.length)
        var carry=0
        for(i in digits.lastIndex downTo 0) {
            val product=(digits[i]-'0')*factor+carry
            result[i]=('0'.code+product%10).toChar();carry=product/10
        }
        digits=(if(carry==0) "" else carry.toString())+result.concatToString()
    }
    var scale=0
    if(binaryExponent>=0) repeat(binaryExponent) { multiply(2) }
    else { repeat(-binaryExponent) { multiply(5) };scale=binaryExponent }
    while(digits.length>1 && digits.last()=='0') { digits=digits.dropLast(1);scale++ }
    val scientificExponent=digits.length-1+scale
    val text=if(scientificExponent in -3..6) {
        val point=digits.length+scale
        when {
            point<=0 -> "0."+"0".repeat(-point)+digits
            point>=digits.length -> digits+"0".repeat(point-digits.length)+".0"
            else -> digits.substring(0,point)+"."+digits.substring(point)
        }
    } else digits.first()+"."+(digits.drop(1).ifEmpty { "0" })+"E"+scientificExponent
    return (if(negative) "-" else "")+text
}
