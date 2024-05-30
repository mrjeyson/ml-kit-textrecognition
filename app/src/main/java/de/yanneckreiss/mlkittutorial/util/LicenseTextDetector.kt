package de.yanneckreiss.mlkittutorial.util

class LicenseTextDetector {

    fun textDetector(text: String, callback: (LicensePlateType, String) -> Unit) {
        val str = text.replace(" ", "").replace("|", "")

        val plateType = when {
            isPhysical(str) -> LicensePlateType.Physical
            isLegal(str) -> LicensePlateType.Legal
            isForeigner(str) -> LicensePlateType.Foreigner
            else -> LicensePlateType.Unknown
        }

        if (plateType != LicensePlateType.Unknown) {
            callback(plateType, str)
        }
    }

    private fun isPhysical(text: String): Boolean {
        return text.length == 8 &&
                text.substring(0, 2).isNumeric() &&
                text.substring(2, 3).isUpperCase() &&
                text.substring(3, 6).isNumeric() &&
                text.substring(6, 8).isUpperCase()
    }

    private fun isLegal(text: String): Boolean {
        return text.length == 8 &&
                text.substring(0, 2).isNumeric() &&
                text.substring(2, 5).isNumeric() &&
                text.substring(5, 8).isUpperCase()
    }

    private fun isForeigner(text: String): Boolean {
        return false
    }

    private fun String.isNumeric(): Boolean {
        return this.isNotEmpty() && this.all { it.isDigit() }
    }

    private fun String.isUpperCase(): Boolean {
        return this.isNotEmpty() && this.all { it.isUpperCase() }
    }
}

enum class LicensePlateType {
    Physical, Legal, Foreigner, Unknown,
}
