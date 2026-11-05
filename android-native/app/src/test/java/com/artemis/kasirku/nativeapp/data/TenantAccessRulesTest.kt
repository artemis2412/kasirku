package com.artemis.kasirku.nativeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TenantAccessRulesTest {
    @Test
    fun cloudRolesAreNormalizedAndKnown() {
        assertEquals(StoreRole.OWNER, StoreRole.fromCloud(" OWNER "))
        assertEquals(StoreRole.ADMIN, StoreRole.fromCloud("admin"))
        assertEquals(StoreRole.SUPERVISOR, StoreRole.fromCloud("Supervisor"))
        assertEquals(StoreRole.CASHIER, StoreRole.fromCloud("cashier"))
    }

    @Test
    fun unknownCloudRoleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoreRole.fromCloud("super_admin")
        }
    }

    @Test
    fun onlyOwnerCanViewCostAndProfit() {
        assertEquals(true, StoreRole.OWNER.canViewCostAndProfit())
        assertEquals(false, StoreRole.ADMIN.canViewCostAndProfit())
        assertEquals(false, StoreRole.SUPERVISOR.canViewCostAndProfit())
        assertEquals(false, StoreRole.CASHIER.canViewCostAndProfit())
    }

    @Test
    fun verifiedContextRejectsMissingIdentityAndTenantFields() {
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedTenantContext("", "store", "Toko", StoreRole.OWNER, "branch", "Cabang")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedTenantContext("user", "", "Toko", StoreRole.OWNER, "branch", "Cabang")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedTenantContext("user", "store", "Toko", StoreRole.OWNER, "", "Cabang")
        }
    }

    @Test
    fun selectedStoreAndBranchMustBelongToAuthorizedAccess() {
        val access = CloudAccountAccess(
            userId = "user-1",
            email = "owner@example.test",
            stores = listOf(
                AuthorizedStore(
                    id = "store-1",
                    name = "Toko Satu",
                    role = StoreRole.OWNER,
                    branches = listOf(AuthorizedBranch("branch-1", "Cabang Satu"))
                )
            )
        )
        assertEquals("branch-1", access.verifiedContext("store-1", "branch-1").branchId)
        assertThrows(IllegalArgumentException::class.java) {
            access.verifiedContext("store-lain", "branch-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            access.verifiedContext("store-1", "branch-lain")
        }
    }
}
