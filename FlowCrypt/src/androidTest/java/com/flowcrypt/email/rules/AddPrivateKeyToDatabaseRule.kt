/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.rules

import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.KeyEntity
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.security.model.PgpKeyRingDetails
import com.flowcrypt.email.util.AccountDaoManager
import com.flowcrypt.email.util.PrivateKeysManager

/**
 * @author Denys Bondarenko
 */
class AddPrivateKeyToDatabaseRule constructor(
  val accountEntity: AccountEntity,
  val keyPath: String,
  val passphrase: String,
  val sourceType: KeyImportDetails.SourceType,
  val passphraseType: KeyEntity.PassphraseType = KeyEntity.PassphraseType.DATABASE
) : BaseRule() {
  var pgpKeyRingDetails: PgpKeyRingDetails
    private set

  init {
    pgpKeyRingDetails = PrivateKeysManager.getPgpKeyDetailsFromAssets(keyPath, true)
  }

  constructor(
    accountEntity: AccountEntity = AccountDaoManager.getDefaultAccountDao(),
    keyPath: String = "pgp/default@flowcrypt.test_fisrtKey_prv_strong.asc",
    passphraseType: KeyEntity.PassphraseType = KeyEntity.PassphraseType.DATABASE
  ) : this(
    accountEntity = accountEntity,
    keyPath = keyPath,
    passphrase = TestConstants.DEFAULT_STRONG_PASSWORD,
    sourceType = KeyImportDetails.SourceType.EMAIL,
    passphraseType = passphraseType
  )

  override fun execute() {
    PrivateKeysManager.saveKeyToDatabase(
      accountEntity = accountEntity,
      pgpKeyRingDetails = pgpKeyRingDetails,
      passphrase = passphrase,
      sourceType = sourceType,
      passphraseType = passphraseType
    )
  }
}
