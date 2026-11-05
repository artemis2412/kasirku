package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CloudAccountAccess
import com.artemis.kasirku.nativeapp.data.CloudAuthRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import kotlinx.coroutines.launch

private val AccountNavy = Color(0xFF17324D)
private val AccountTeal = Color(0xFF168C83)
private val AccountMuted = Color(0xFF718096)

@Composable
internal fun CloudAccountScreen(
    modifier: Modifier = Modifier,
    onContextChanged: (VerifiedTenantContext?) -> Unit
) {
    val repository = remember { CloudAuthRepository() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var access by remember { mutableStateOf<CloudAccountAccess?>(null) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var selectedBranchId by remember { mutableStateOf<String?>(null) }
    var verifiedContext by remember { mutableStateOf<VerifiedTenantContext?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Masuk menggunakan akun Cloud Kasirku.") }
    var isError by remember { mutableStateOf(false) }

    fun applyAccess(result: CloudAccountAccess) {
        access = result
        selectedStoreId = result.stores.singleOrNull()?.id
        selectedBranchId = result.stores.singleOrNull()?.branches?.singleOrNull()?.id
        verifiedContext = null
        onContextChanged(null)
        message = if (result.stores.size > 1) {
            "Pilih toko yang akan digunakan."
        } else {
            "Pilih cabang aktif untuk melanjutkan."
        }
        isError = false
    }

    LaunchedEffect(Unit) {
        busy = true
        runCatching { repository.restoreSession() }
            .onSuccess { result -> if (result != null) applyAccess(result) }
            .onFailure { message = it.message ?: "Gagal memulihkan sesi Cloud."; isError = true }
        busy = false
    }

    val selectedStore = access?.stores?.firstOrNull { it.id == selectedStoreId }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("AKUN & AKSES", color = AccountTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Akun Cloud", color = AccountNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Masuk, lalu pilih toko dan cabang yang memang diizinkan untuk akunmu.", color = AccountMuted, fontSize = 13.sp)
        }

        if (access == null) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kata sandi") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        isError = false
                        message = "Memverifikasi akun, toko, dan cabang…"
                        runCatching { repository.signIn(email, password) }
                            .onSuccess(::applyAccess)
                            .onFailure {
                                message = it.message ?: "Login Cloud gagal."
                                isError = true
                            }
                        busy = false
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text(if (busy) "Memeriksa…" else "Masuk ke Cloud") }
        } else {
            Text("Pilih toko", color = AccountNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            access!!.stores.forEach { store ->
                SelectionCard(
                    title = store.name,
                    subtitle = "Peran: ${store.role.name.lowercase()}",
                    selected = selectedStoreId == store.id,
                    onClick = {
                        selectedStoreId = store.id
                        selectedBranchId = null
                        verifiedContext = null
                        onContextChanged(null)
                    }
                )
            }

            if (selectedStore != null) {
                Spacer(Modifier.height(2.dp))
                Text("Pilih cabang", color = AccountNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                selectedStore.branches.forEach { branch ->
                    SelectionCard(
                        title = branch.name,
                        subtitle = "Cabang aktif",
                        selected = selectedBranchId == branch.id,
                        onClick = {
                            selectedBranchId = branch.id
                            verifiedContext = access!!.verifiedContext(selectedStore.id, branch.id)
                            onContextChanged(verifiedContext)
                            message = "Akses toko dan cabang berhasil diverifikasi."
                            isError = false
                        }
                    )
                }
            }

            verifiedContext?.let { context ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4F1)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("KONTEKS TERVERIFIKASI", color = AccountTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(context.storeName, color = AccountNavy, fontWeight = FontWeight.Bold)
                        Text(context.branchName + " • " + context.role.name.lowercase(), color = AccountMuted, fontSize = 12.sp)
                        Text("Akses harga modal/laba: " + if (context.role.canViewCostAndProfit()) "Owner" else "Tidak diizinkan", color = AccountMuted, fontSize = 12.sp)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        runCatching { repository.signOut() }
                        access = null
                        verifiedContext = null
                        selectedStoreId = null
                        selectedBranchId = null
                        onContextChanged(null)
                        message = "Sesi Cloud telah diakhiri."
                        busy = false
                    }
                }, enabled = !busy) { Text("Keluar dari Cloud") }
            }
        }

        Text(
            message,
            color = if (isError) Color(0xFFB42318) else AccountMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F4F1) else Color.White
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AccountNavy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = AccountMuted, fontSize = 11.sp)
        }
    }
}
