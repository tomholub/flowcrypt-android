/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.sync.tasks;

import android.os.Messenger;
import android.support.annotation.NonNull;
import android.util.LongSparseArray;

import com.flowcrypt.email.api.email.sync.SyncListener;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.sun.mail.gimap.GmailRawSearchTerm;
import com.sun.mail.imap.IMAPFolder;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

/**
 * This task finds messages on some folder.
 *
 * @author DenBond7
 *         Date: 26.04.2018
 *         Time: 14:20
 *         E-mail: DenBond7@gmail.com
 */

public class SearchMessagesSyncTask extends BaseSyncTask {
    private static final int COUNT_OF_LOADED_EMAILS_BY_STEP = 20;
    private com.flowcrypt.email.api.email.Folder folder;
    private int countOfAlreadyLoadedMessages;

    /**
     * The base constructor.
     *
     * @param ownerKey    The name of the reply to {@link Messenger}.
     * @param requestCode The unique request code for the reply to {@link Messenger}.
     */
    public SearchMessagesSyncTask(String ownerKey, int requestCode, com.flowcrypt.email.api.email.Folder folder,
                                  int countOfAlreadyLoadedMessages) {
        super(ownerKey, requestCode);
        this.folder = folder;
        this.countOfAlreadyLoadedMessages = countOfAlreadyLoadedMessages;
    }

    @Override
    public void runIMAPAction(AccountDao accountDao, Session session, Store store, SyncListener syncListener)
            throws Exception {
        super.runIMAPAction(accountDao, session, store, syncListener);

        IMAPFolder imapFolder = (IMAPFolder) store.getFolder(folder.getServerFullFolderName());
        imapFolder.open(Folder.READ_ONLY);

        if (countOfAlreadyLoadedMessages < 0) {
            countOfAlreadyLoadedMessages = 0;
        }

        Message[] foundMessages = imapFolder.search(generateSearchTerm(accountDao));

        int messagesCount = foundMessages.length;
        int end = messagesCount - countOfAlreadyLoadedMessages;
        int start = end - COUNT_OF_LOADED_EMAILS_BY_STEP + 1;

        if (syncListener != null) {
            if (end < 1) {
                syncListener.onSearchMessagesReceived(accountDao, folder, imapFolder, new Message[]{}, new
                        LongSparseArray<Boolean>(), ownerKey, requestCode);
            } else {
                if (start < 1) {
                    start = 1;
                }

                Message[] bufferedMessages = new Message[end - start + 1];
                System.arraycopy(foundMessages, start - 1, bufferedMessages, 0, end - start + 1);

                FetchProfile fetchProfile = new FetchProfile();
                fetchProfile.add(FetchProfile.Item.ENVELOPE);
                fetchProfile.add(FetchProfile.Item.FLAGS);
                fetchProfile.add(FetchProfile.Item.CONTENT_INFO);
                fetchProfile.add(UIDFolder.FetchProfileItem.UID);
                imapFolder.fetch(bufferedMessages, fetchProfile);

                //todo-denbond7 need to improve it
                syncListener.onSearchMessagesReceived(accountDao, folder, imapFolder, bufferedMessages,
                        new LongSparseArray<Boolean>(),
                        ownerKey, requestCode);
            }
        }

        imapFolder.close(false);
    }

    /**
     * Generate a {@link SearchTerm} depend on an input {@link AccountDao}.
     *
     * @param accountDao An input {@link AccountDao}
     * @return A generated {@link SearchTerm}.
     */
    @NonNull
    private SearchTerm generateSearchTerm(AccountDao accountDao) {
        if (AccountDao.ACCOUNT_TYPE_GOOGLE.equalsIgnoreCase(accountDao.getAccountType())) {
            return new GmailRawSearchTerm(folder.getSearchQuery());
        } else {
            return new SubjectTerm(folder.getSearchQuery());
        }
    }
}
