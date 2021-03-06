package cn.ucai.superwechat.parse;

import android.content.Context;
import android.content.Intent;

import com.hyphenate.EMValueCallBack;
import com.hyphenate.chat.EMClient;

import cn.ucai.superwechat.I;
import cn.ucai.superwechat.R;
import cn.ucai.superwechat.SuperWeChatApplication;
import cn.ucai.superwechat.SuperWeChatHelper;
import cn.ucai.superwechat.SuperWeChatHelper.DataSyncListener;
import cn.ucai.superwechat.bean.Result;
import cn.ucai.superwechat.db.UserDao;
import cn.ucai.superwechat.model.IUserModel;
import cn.ucai.superwechat.model.OnCompleteListener;
import cn.ucai.superwechat.model.UserModel;
import cn.ucai.superwechat.ui.UserProfileActivity;
import cn.ucai.superwechat.utils.CommonUtils;
import cn.ucai.superwechat.utils.L;
import cn.ucai.superwechat.utils.PreferenceManager;
import cn.ucai.superwechat.utils.ResultUtils;
import cn.ucai.superwechat.utils.SharePrefrenceUtils;

import com.hyphenate.easeui.domain.EaseUser;
import com.hyphenate.easeui.domain.User;
import com.hyphenate.easeui.utils.EaseUserUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UserProfileManager {
	private static final String TAG = UserProfileManager.class.getSimpleName();

	/**
	 * application context
	 */
	protected Context appContext = null;

	/**
	 * init flag: test if the sdk has been inited before, we don't need to init
	 * again
	 */
	private boolean sdkInited = false;
	IUserModel userModel;

	/**
	 * HuanXin sync contact nick and avatar listener
	 */
	private List<DataSyncListener> syncContactInfosListeners;

	private boolean isSyncingContactInfosWithServer = false;

	private EaseUser currentUser;//环信服务器当前用户
	private User currentAppUser;//App服务器当前用户
	public UserProfileManager() {
	}

	public synchronized boolean init(Context context) {
		if (sdkInited) {
			return true;
		}
		ParseManager.getInstance().onInit(context);
		syncContactInfosListeners = new ArrayList<DataSyncListener>();
		sdkInited = true;
		userModel = new UserModel();
		appContext = context;
		return true;
	}

	public void addSyncContactInfoListener(DataSyncListener listener) {
		if (listener == null) {
			return;
		}
		if (!syncContactInfosListeners.contains(listener)) {
			syncContactInfosListeners.add(listener);
		}
	}

	public void removeSyncContactInfoListener(DataSyncListener listener) {
		if (listener == null) {
			return;
		}
		if (syncContactInfosListeners.contains(listener)) {
			syncContactInfosListeners.remove(listener);
		}
	}

	public void asyncFetchContactInfosFromServer(List<String> usernames, final EMValueCallBack<List<EaseUser>> callback) {
		if (isSyncingContactInfosWithServer) {
			return;
		}
		isSyncingContactInfosWithServer = true;
		ParseManager.getInstance().getContactInfos(usernames, new EMValueCallBack<List<EaseUser>>() {

			@Override
			public void onSuccess(List<EaseUser> value) {
				isSyncingContactInfosWithServer = false;
				// in case that logout already before server returns,we should
				// return immediately
				if (!SuperWeChatHelper.getInstance().isLoggedIn()) {
					return;
				}
				if (callback != null) {
					callback.onSuccess(value);
				}
			}

			@Override
			public void onError(int error, String errorMsg) {
				isSyncingContactInfosWithServer = false;
				if (callback != null) {
					callback.onError(error, errorMsg);
				}
			}

		});

	}

	public void notifyContactInfosSyncListener(boolean success) {
		for (DataSyncListener listener : syncContactInfosListeners) {
			listener.onSyncComplete(success);
		}
	}

	public boolean isSyncingContactInfoWithServer() {
		return isSyncingContactInfosWithServer;
	}

	public synchronized void reset() {
		isSyncingContactInfosWithServer = false;
		currentUser = null;
		currentAppUser = null;
		PreferenceManager.getInstance().removeCurrentUserInfo();
	}

	public synchronized EaseUser getCurrentUserInfo() {
		if (currentUser == null) {
			String username = EMClient.getInstance().getCurrentUser();
			currentUser = new EaseUser(username);
			String nick = getCurrentUserNick();
			currentUser.setNick((nick != null) ? nick : username);
			currentUser.setAvatar(getCurrentUserAvatar());
		}
		return currentUser;
	}
	public synchronized User getCurrentAppUserInfo(){
		if (currentAppUser == null || currentAppUser.getMUserName() == null){
			String username = EMClient.getInstance().getCurrentUser();//获取环信服务器上的用户名
			currentAppUser = new User(username);//创建我们服务器所要保存的User对象
			String nick = getCurrentUserNick();//获取环信服务器上的用户昵称
			currentAppUser.setMUserNick((nick != null) ? nick : username);//设置服务器保存的用户昵称
			currentAppUser.setAvatar(getCurrentUserAvatar());//设置服务器保存的用户头像
		}
		return currentAppUser;
	}

	public boolean updateCurrentUserNickName(final String nickname) {
		boolean isSuccess = ParseManager.getInstance().updateParseNickName(nickname);
		if (isSuccess) {
			setCurrentUserNick(nickname);
		}
		return isSuccess;
	}

	public void uploadUserAvatar(File file) {
		userModel.updateUserAvatar(appContext, EMClient.getInstance().getCurrentUser(), file,
				new OnCompleteListener<String>() {
					@Override
					public void onSuccess(String r) {
						L.e(TAG, "uploadAppUserAvatar,r = " + r);
						boolean success = false;
						if (r != null) {
							Result result = ResultUtils.getResultFromJson(r, User.class);
							L.e(TAG, "uploadAppUserAvatar,result = " + result);
							if (result != null && result.isRetMsg()) {
								final User user = (User) result.getRetData();
								L.e(TAG, "uploadAppUserAvatar,user = " + user);
								if (user != null) {
									success = true;
									L.e(TAG, "uploadAppUserAvatar,user = " + user);
									setCurrentAppUserAvatar(user.getAvatar());
									SuperWeChatHelper.getInstance().saveAppContact(user);
								}
							}
						}
						appContext.sendBroadcast(new Intent(I.REQUEST_UPDATE_AVATAR)
								.putExtra(I.Avatar.UPDATE_TIME, success));
					}

					@Override
					public void onError(String error) {
						L.e(TAG,"onError,error = " + error);
						appContext.sendBroadcast(new Intent(I.REQUEST_UPDATE_AVATAR)
								.putExtra(I.Avatar.UPDATE_TIME, false));
					}
				});
		/*String avatarUrl = ParseManager.getInstance().uploadParseAvatar(data);
		if (avatarUrl != null) {
			setCurrentUserAvatar(avatarUrl);
		}
		return avatarUrl;*/
	}
	public void asyncGetAppCurrentUserInfo() {
		userModel.loadUserInfo(appContext,EMClient.getInstance().getCurrentUser(),
				new OnCompleteListener<String>() {
			@Override
			public void onSuccess(String result) {
				L.e(TAG,"asyncGetAppCurrentUserInfo(),result = " + result);
				if (result != null){
					L.e(TAG,"asyncGetAppCurrentUserInfo(),result1 = " + result);
					Result res = ResultUtils.getResultFromJson(result, User.class);
					if (res != null && res.isRetMsg()){
						User user = (User) res.getRetData();
						L.e(TAG,"asyncGetAppCurrentUserInfo(),user = " + user);
						//将数据保存到首选项、内存和数据库中
						if (user != null){
							currentAppUser = user;
							setCurrentAppUserNick(user.getMUserNick());
							L.e(TAG,"user.getMUserNick() = " + user.getMUserNick());
							setCurrentAppUserAvatar(user.getAvatar());
							SuperWeChatHelper.getInstance().saveAppContact(user);
						}
					}
				}
			}
			@Override
			public void onError(String error) {
				L.e(TAG,"error = " + error);
			}
		});
	}

	public void asyncGetCurrentUserInfo() {
		ParseManager.getInstance().asyncGetCurrentUserInfo(new EMValueCallBack<EaseUser>() {
			@Override
			public void onSuccess(EaseUser value) {
			    if(value != null){
    				setCurrentUserNick(value.getNick());
    				setCurrentUserAvatar(value.getAvatar());
			    }
			}
			@Override
			public void onError(int error, String errorMsg) {

			}
		});
	}
	public void asyncGetUserInfo(final String username,final EMValueCallBack<EaseUser> callback){
		ParseManager.getInstance().asyncGetUserInfo(username, callback);
	}
	private void setCurrentUserNick(String nickname) {
		getCurrentUserInfo().setNick(nickname);
		PreferenceManager.getInstance().setCurrentUserNick(nickname);
	}

	private void setCurrentUserAvatar(String avatar) {
		getCurrentUserInfo().setAvatar(avatar);
		PreferenceManager.getInstance().setCurrentUserAvatar(avatar);
	}

	private void setCurrentAppUserNick(String nickname){
		getCurrentAppUserInfo().setMUserNick(nickname);//保存用户昵称到内存中
		PreferenceManager.getInstance().setCurrentUserNick(nickname);//保存用户昵称到Shareprefrence中
	}
	private void setCurrentAppUserAvatar(String avatar){
		getCurrentAppUserInfo().setAvatar(avatar);//保存用户头像到内存中
		PreferenceManager.getInstance().setCurrentUserAvatar(avatar);//保存用户头像到Shareprefrence中
	}

	private String getCurrentUserNick() {
		return PreferenceManager.getInstance().getCurrentUserNick();
	}

	private String getCurrentUserAvatar() {
		return PreferenceManager.getInstance().getCurrentUserAvatar();
	}
}
