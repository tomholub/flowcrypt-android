/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.model

import android.os.Parcel
import android.os.Parcelable
import com.flowcrypt.email.api.email.EmailUtil
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * @author Denis Bondarenko
 *         Date: 10/29/19
 *         Time: 11:30 AM
 *         E-mail: DenBond7@gmail.com
 */
data class OrgRules constructor(
  @Expose val flags: List<DomainRule>? = null,
  @SerializedName("custom_keyserver_url")
  @Expose val customKeyserverUrl: String? = null,
  @SerializedName("key_manager_url")
  @Expose val keyManagerUrl: String? = null,
  @SerializedName("disallow_attester_search_for_domains")
  @Expose val disallowAttesterSearchForDomains: List<String>? = null,
  @SerializedName("allow_attester_search_only_for_domains")
  @Expose val allowAttesterSearchOnlyForDomains: List<String>? = null,
  @SerializedName("enforce_keygen_algo")
  @Expose val enforceKeygenAlgo: KeyAlgo? = null,
  @SerializedName("enforce_keygen_expire_months")
  @Expose val enforceKeygenExpireMonths: Int? = null,
  @SerializedName("in_memory_pass_phrase_session_length")
  @Expose val inMemoryPassPhraseSessionLength: Int? = 60
) : Parcelable {

  val inMemoryPassPhraseSessionLengthNormalized: Int?
    get() {
      val value = inMemoryPassPhraseSessionLength ?: 0
      return if (value > 0) {
        minOf(value, Int.MAX_VALUE)
      } else null
    }

  constructor(parcel: Parcel) : this(
    parcel.createTypedArrayList(DomainRule.CREATOR),
    parcel.readString(),
    parcel.readString(),
    parcel.createStringArrayList(),
    parcel.createStringArrayList(),
    parcel.readParcelable(KeyAlgo::class.java.classLoader),
    parcel.readValue(Int::class.java.classLoader) as? Int,
    parcel.readValue(Int::class.java.classLoader) as? Int
  )

  override fun writeToParcel(parcel: Parcel, flagsList: Int) {
    parcel.writeTypedList(flags)
    parcel.writeString(customKeyserverUrl)
    parcel.writeString(keyManagerUrl)
    parcel.writeStringList(disallowAttesterSearchForDomains)
    parcel.writeStringList(allowAttesterSearchOnlyForDomains)
    parcel.writeParcelable(enforceKeygenAlgo, flagsList)
    parcel.writeValue(enforceKeygenExpireMonths)
    parcel.writeValue(inMemoryPassPhraseSessionLength)
  }

  override fun describeContents(): Int {
    return 0
  }

  /**
   * Internal company SKS-like public key server to trust above Attester
   */
  fun getCustomSksPubKeyServer(): String? {
    return customKeyserverUrl
  }

  /**
   * an internal org FlowCrypt Email Key Manager instance, can manage both public and private keys
   * use this method when using for PRV sync
   */
  fun getKeyManagerUrlForPrivateKeys(): String? {
    return keyManagerUrl
  }

  /**
   * an internal org FlowCrypt Email Key Manager instance, can manage both public and private keys
   * use this method when using for PUB sync
   */
  fun getKeyManagerUrlForPublicKeys(): String? {
    if (hasRule(DomainRule.NO_KEY_MANAGER_PUB_LOOKUP)) {
      return null
    }
    return keyManagerUrl
  }

  /**
   * use when finding out if EKM is in use,
   * to change functionality without actually needing the EKM
   */
  fun usesKeyManager(): Boolean {
    return keyManagerUrl != null
  }

  /**
   * Enforce a key algo for keygen, eg rsa2048,rsa4096,curve25519
   */
  fun getEnforcedKeygenAlgo(): KeyAlgo? {
    return enforceKeygenAlgo
  }

  /**
   * Some orgs want to have newly generated keys include self-signatures that expire some time
   * in the future.
   */
  fun getEnforcedKeygenExpirationMonths(): Int? {
    return enforceKeygenExpireMonths
  }

  /**
   * Some orgs expect 100% of their private keys to be imported from elsewhere
   * (and forbid keygen in the extension)
   */
  fun canCreateKeys(): Boolean {
    return !hasRule(DomainRule.NO_PRV_CREATE)
  }

  /**
   * Some orgs want to forbid backing up of public keys (such as inbox or other methods)
   */
  fun canBackupKeys(): Boolean {
    return !hasRule(DomainRule.NO_PRV_BACKUP)
  }

  /**
   * (normally, during setup, if a public key is submitted to Attester and there is
   * a conflicting key already submitted, the issue will be skipped) some orgs want to make sure
   * that their public key gets submitted to attester and conflict errors are NOT ignored:
   */
  fun mustSubmitToAttester(): Boolean {
    return hasRule(DomainRule.ENFORCE_ATTESTER_SUBMIT)
  }

  /**
   * Normally, during setup, "remember pass phrase" is unchecked
   * This option will cause "remember pass phrase" option to be checked by default
   * This behavior is also enabled as a byproduct of PASS_PHRASE_QUIET_AUTOGEN
   */
  fun rememberPassPhraseByDefault(): Boolean {
    return hasRule(DomainRule.DEFAULT_REMEMBER_PASS_PHRASE) || this.mustAutoGenPassPhraseQuietly()
  }

  fun forbidStoringPassPhrase(): Boolean {
    return hasRule(DomainRule.FORBID_STORING_PASS_PHRASE)
  }

  fun forbidCreatingPrivateKey(): Boolean {
    return hasRule(DomainRule.NO_PRV_CREATE)
  }

  /**
   * This is to be used for customers who run their own FlowCrypt Email Key Manager
   * If a key can be found on FEKM, it will be auto imported
   * If not, it will be autogenerated and stored there
   */
  fun mustAutoImportOrAutoGenPrvWithKeyManager(): Boolean {
    if (!hasRule(DomainRule.PRV_AUTOIMPORT_OR_AUTOGEN)) {
      return false
    }
    if (getKeyManagerUrlForPrivateKeys() == null) {
      throw IllegalStateException(
        "Wrong org rules config: using PRV_AUTOIMPORT_OR_AUTOGEN without key_manager_url"
      )
    }
    return true
  }

  /**
   * When generating keys, user will not be prompted to choose a pass phrase
   * Instead a pass phrase will be automatically generated, and stored locally
   * The pass phrase will NOT be displayed to user, and it will never be asked of the user
   * This creates the smoothest user experience, for organisations that use
   * full-disk-encryption and don't need pass phrase protection
   */
  fun mustAutoGenPassPhraseQuietly(): Boolean {
    return hasRule(DomainRule.PASS_PHRASE_QUIET_AUTOGEN)
  }

  /**
   * Some orgs prefer to forbid publishing public keys publicly
   */
  fun canSubmitPubToAttester(): Boolean {
    return !hasRule(DomainRule.NO_ATTESTER_SUBMIT)
  }

  /**
   * Some orgs have a list of email domains where they do NOT want OR want such emails to be looked up on
   * public sources (such as Attester). This is because they already have other means to obtain
   * public keys for these domains, such as from their own internal keyserver.
   */
  fun canLookupThisRecipientOnAttester(emailAddr: String): Boolean {
    val userDomain = EmailUtil.getDomain(emailAddr)
    if (userDomain.isEmpty()) {
      throw IllegalStateException("Not a valid email $emailAddr")
    }

    val allowedDomains = allowAttesterSearchOnlyForDomains
    return if (allowedDomains != null) {
      allowedDomains.any { it.equals(userDomain, true) }
    } else {
      val disallowedDomains = disallowAttesterSearchForDomains ?: emptyList()
      if (disallowedDomains.contains("*")) {
        false
      } else disallowedDomains.none { it.equals(userDomain, true) }
    }
  }

  /**
   * Some orgs use flows that are only implemented in POST /initial/legacy_submit
   * and not in POST /pub/email@corp.co: -> enforcing that submitted keys match customer key server
   * Until the newer endpoint is ready, this flag will point users in those orgs to
   * the original endpoint
   */
  fun useLegacyAttesterSubmit(): Boolean {
    return hasRule(DomainRule.USE_LEGACY_ATTESTER_SUBMIT)
  }

  /**
   * With this option, sent messages won't have any comment/version in armor,
   * imported keys get imported without armor
   */
  fun shouldHideArmorMeta(): Boolean {
    return hasRule(DomainRule.HIDE_ARMOR_META)
  }

  fun hasRule(domainRule: DomainRule): Boolean {
    return flags?.firstOrNull { it == domainRule } != null
  }

  enum class DomainRule : Parcelable {
    NO_PRV_CREATE,
    NO_PRV_BACKUP,
    PRV_AUTOIMPORT_OR_AUTOGEN,
    PASS_PHRASE_QUIET_AUTOGEN,
    ENFORCE_ATTESTER_SUBMIT,
    NO_ATTESTER_SUBMIT,
    NO_KEY_MANAGER_PUB_LOOKUP,
    USE_LEGACY_ATTESTER_SUBMIT,
    DEFAULT_REMEMBER_PASS_PHRASE,
    HIDE_ARMOR_META,
    FORBID_STORING_PASS_PHRASE,
    RESTRICT_ANDROID_ATTACHMENT_HANDLING;

    companion object CREATOR : Parcelable.Creator<DomainRule> {
      override fun createFromParcel(parcel: Parcel): DomainRule = values()[parcel.readInt()]
      override fun newArray(size: Int): Array<DomainRule?> = arrayOfNulls(size)
    }

    override fun describeContents(): Int {
      return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeInt(ordinal)
    }
  }

  enum class KeyAlgo : Parcelable {
    curve25519,
    rsa2048,
    rsa4096;

    companion object CREATOR : Parcelable.Creator<KeyAlgo> {
      override fun createFromParcel(parcel: Parcel): KeyAlgo = values()[parcel.readInt()]
      override fun newArray(size: Int): Array<KeyAlgo?> = arrayOfNulls(size)
    }

    override fun describeContents(): Int {
      return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeInt(ordinal)
    }
  }

  companion object CREATOR : Parcelable.Creator<OrgRules> {
    override fun createFromParcel(parcel: Parcel): OrgRules {
      return OrgRules(parcel)
    }

    override fun newArray(size: Int): Array<OrgRules?> {
      return arrayOfNulls(size)
    }
  }
}
