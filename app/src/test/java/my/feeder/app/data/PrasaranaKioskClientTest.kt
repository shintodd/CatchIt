package my.feeder.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PrasaranaKioskClientTest {

    @Test
    fun `stripDirectionSuffix normalizes Klang Valley feeder routes`() {
        assertEquals("T507", PrasaranaKioskClient.stripDirectionSuffix("T5070"))
        assertEquals("T507", PrasaranaKioskClient.stripDirectionSuffix("T5071"))
        assertEquals("T155", PrasaranaKioskClient.stripDirectionSuffix("T1550"))
        assertEquals("T155", PrasaranaKioskClient.stripDirectionSuffix("T1551"))
        assertEquals("T852", PrasaranaKioskClient.stripDirectionSuffix("T8520"))
    }

    @Test
    fun `stripDirectionSuffix normalizes trunk routes with prefix or direction suffix`() {
        assertEquals("420", PrasaranaKioskClient.stripDirectionSuffix("U4200"))
        assertEquals("420", PrasaranaKioskClient.stripDirectionSuffix("4200"))
        assertEquals("600", PrasaranaKioskClient.stripDirectionSuffix("6000"))
        assertEquals("250", PrasaranaKioskClient.stripDirectionSuffix("2500"))
    }

    @Test
    fun `stripDirectionSuffix maps trunk B prefix routes correctly`() {
        assertEquals("100", PrasaranaKioskClient.stripDirectionSuffix("B1000"))
        assertEquals("100", PrasaranaKioskClient.stripDirectionSuffix("B100"))
    }

    @Test
    fun `stripDirectionSuffix maps Petaling Jaya City Bus and Smart Selangor`() {
        assertEquals("PJ01", PrasaranaKioskClient.stripDirectionSuffix("P0010"))
        assertEquals("PJ02", PrasaranaKioskClient.stripDirectionSuffix("P0020"))
        assertEquals("SA07", PrasaranaKioskClient.stripDirectionSuffix("S4070"))
        assertEquals("KJ01", PrasaranaKioskClient.stripDirectionSuffix("S0030"))
        assertEquals("AJ01", PrasaranaKioskClient.stripDirectionSuffix("S0040"))
    }

    @Test
    fun `stripDirectionSuffix preserves Rapid Penang routes without stripping legitimate digits`() {
        assertEquals("101", PrasaranaKioskClient.stripDirectionSuffix("101"))
        assertEquals("11", PrasaranaKioskClient.stripDirectionSuffix("11"))
        assertEquals("CT13", PrasaranaKioskClient.stripDirectionSuffix("CT13"))
        assertEquals("CT15", PrasaranaKioskClient.stripDirectionSuffix("CT15"))
        assertEquals("CT14", PrasaranaKioskClient.stripDirectionSuffix("CAT14"))
        assertEquals("EB60", PrasaranaKioskClient.stripDirectionSuffix("EB60"))
        assertEquals("EB80", PrasaranaKioskClient.stripDirectionSuffix("EB80"))
        assertEquals("T713", PrasaranaKioskClient.stripDirectionSuffix("T713"))
    }

    @Test
    fun `stripDirectionSuffix handles feeder loop variants`() {
        assertEquals("T406B", PrasaranaKioskClient.stripDirectionSuffix("T4069"))
        assertEquals("T400B", PrasaranaKioskClient.stripDirectionSuffix("T4001"))
        assertEquals("T512B", PrasaranaKioskClient.stripDirectionSuffix("T5121"))
        assertEquals("T810", PrasaranaKioskClient.stripDirectionSuffix("T810B"))
    }
}
