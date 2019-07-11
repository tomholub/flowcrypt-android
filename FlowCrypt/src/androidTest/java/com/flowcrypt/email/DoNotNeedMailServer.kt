/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email

/**
 * This annotation indicates that a whole class or a single method doesn't need an email server for successful
 * completion.
 *
 * @author Denis Bondarenko
 *         Date: 7/11/19
 *         Time: 2:28 PM
 *         E-mail: DenBond7@gmail.com
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DoNotNeedMailServer