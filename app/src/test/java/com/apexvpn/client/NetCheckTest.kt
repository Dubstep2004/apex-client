package com.apexvpn.client

import com.apexvpn.client.data.NetCheck
import com.apexvpn.client.data.NetCheck.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3.21: вердикт «Проверки сети» по результатам этапов. */
class NetCheckTest {
    private fun v(ru: Boolean, world: Boolean, normal: Boolean, lte: Boolean, nodes: Boolean = true, pok: Int = 0, pall: Int = 0) =
        NetCheck.verdict(ru, world, normal, lte, nodes, pok, pall)

    @Test fun everythingWorks() = assertEquals(Kind.Ok, v(ru = true, world = true, normal = true, lte = true, pok = 3, pall = 3))
    @Test fun noInternet() = assertEquals(Kind.NoInternet, v(ru = false, world = false, normal = false, lte = false))
    @Test fun whiteListEvenIfTcpPasses() = assertEquals(Kind.WhiteList, v(ru = true, world = false, normal = true, lte = true))
    @Test fun whiteListNoBypass() = assertEquals(Kind.WhiteList, v(ru = true, world = false, normal = false, lte = false))
    @Test fun serversBlocked() = assertEquals(Kind.ServersBlocked, v(ru = true, world = true, normal = false, lte = false))
    @Test fun jammedAll() = assertEquals(Kind.Jammed, v(ru = true, world = true, normal = true, lte = false, pok = 0, pall = 3))
    @Test fun jammedPart() = assertEquals(Kind.JamPartial, v(ru = true, world = true, normal = true, lte = true, pok = 1, pall = 3))
    @Test fun vpnOffNoProtocols() = assertEquals(Kind.Ok, v(ru = true, world = true, normal = true, lte = false))
    @Test fun noSubscription() = assertEquals(Kind.Ok, v(ru = true, world = true, normal = false, lte = false, nodes = false))
}

/** 3.21.2: строка «Приложения» — напрямую и через VPN. */
class NetCheckAppsTest {
    private fun m(d: Int, v: Int, on: Boolean) = NetCheck.appItem("X", d, v, on).mark
    @Test fun blockedButVpnWorks() = assertEquals(NetCheck.Mark.Ok, m(-1, 120, true))
    @Test fun bothFail() = assertEquals(NetCheck.Mark.Fail, m(-1, -1, true))
    @Test fun onlyDirect() = assertEquals(NetCheck.Mark.Slow, m(80, -1, true))
    @Test fun vpnOffBlocked() = assertEquals(NetCheck.Mark.Fail, m(-1, -1, false))
    @Test fun vpnOffOpen() = assertEquals(NetCheck.Mark.Ok, m(50, -1, false))
}
