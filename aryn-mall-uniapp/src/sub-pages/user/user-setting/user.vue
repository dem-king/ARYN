<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { editObj } from '@/api/user/user'
import { uploadFile } from '@/api/upms/file'

definePage({
  name: 'user-setting',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '用户信息',
  },
})

interface Form {
  id: string | undefined
  avatarUrl: string | undefined
  nickname: string | undefined
  sex: string | undefined
  phone: string | undefined
  password: string | undefined
}

const userStore = useUserStore()

const globalLoading = useGlobalLoading()
const router = useRouter()
const formRef = ref()
const state = reactive<{ form: Form }>({
  form: {
    id: '',
    avatarUrl: '',
    nickname: '',
    sex: '',
    phone: '',
    password: '',
  },
})
onLoad(() => {
  state.form.id = userStore.getUserId
  state.form.avatarUrl = userStore.getUserAvatar
  state.form.nickname = userStore.getUserNickname
  state.form.sex = userStore.getUserInfo?.sex
  state.form.phone = userStore.getUserPhone
  state.form.password = userStore.getUserInfo?.password
})
const sexColumns = ref<any>([
  {
    value: '1',
    label: '男',
  },
  {
    value: '2',
    label: '女',
  },
])
function chooseImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['original', 'compressed'], // 可以指定是原图还是压缩图，默认二者都有
    sourceType: ['album'], // 从相册选择
    success(res) {
      globalLoading.loading('上传中...')
      uploadFile(res.tempFilePaths[0]).then((response) => {
        state.form.avatarUrl = (response as any).data
      }).finally(() => {
        globalLoading.close()
      })
    },
  })
}
async function onChooseAvatar(e: any) {
  const { avatarUrl } = e.detail
  state.form.avatarUrl = await uploadImg(avatarUrl)
}
function edit() {
  formRef.value
    .validate()
    .then(({ valid }: any) => {
      if (valid) {
        globalLoading.loading('修改中...')
        editObj({
          id: state.form.id,
          sex: state.form.sex,
          nickname: state.form.nickname,
          avatarUrl: state.form.avatarUrl,
        }).then(() => {
          userStore.fetchUserInfo()
          const pages = getCurrentPages()
          if (pages.length > 1) {
            router.back()
          }
          else {
            router.pushTab({ name: 'home' })
          }
        }).finally(() => {
          globalLoading.close()
        })
      }
    })
}
</script>

<template>
  <hr-navbar title="用户信息" />
  <view class="setting-container">
    <view class="setting-warp bg-white">
      <!-- 头像上传区域 -->
      <view class="avatar-section">
        <!-- #ifdef MP -->
        <button class="avatar-wrap" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
          <image v-if="state.form.avatarUrl" class="avatar" :src="state.form.avatarUrl" mode="aspectFill" />
          <view v-else class="avatar-placeholder">
            <wd-icon name="fill-camera" size="32px" color="#999999" />
          </view>
          <view class="change-text">
            {{ state.form.avatarUrl ? '更换头像' : '上传头像' }}
          </view>
        </button>
        <!-- #endif -->
        <!-- #ifndef MP -->
        <view class="avatar-wrap" @click="chooseImage">
          <image v-if="state.form.avatarUrl" class="avatar" :src="state.form.avatarUrl" mode="aspectFill" />
          <view v-else class="avatar-placeholder">
            <wd-icon name="fill-camera" size="32px" color="#999999" />
          </view>
          <view class="change-text">
            {{ state.form.avatarUrl ? '更换头像' : '上传头像' }}
          </view>
        </view>
        <!-- #endif -->
      </view>
      <view class="setting-content">
        <wd-form ref="formRef" :model="state.form">
          <wd-cell-group border>
            <wd-input
              v-model="state.form.nickname" label="昵称" type="nickname" label-width="60px"
              prop="nickname" center placeholder="请输入昵称"
              :rules="[{ required: true, message: '请填写昵称' }]"
            />
            <wd-select-picker
              v-model="state.form.sex" label-width="60px" label="性别" title="请选择性别"
              :show-confirm="false" :columns="sexColumns" type="radio" prop="sex"
              :rules="[{ required: true, message: '请选择性别' }]"
            />
            <wd-cell />
          </wd-cell-group>
        </wd-form>
        <wd-button type="primary" block @click="edit">
          确认修改
        </wd-button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.setting-container {
    padding: 20rpx;

    .setting-warp {
        border-radius: 10rpx;

        .avatar-section {
            display: flex;
            justify-content: center;
            padding: 40rpx 0;

            .avatar-wrap {
                position: relative;
                width: 160rpx;
                height: 160rpx;
                border-radius: 80rpx;
                overflow: hidden;
                background-color: #f5f5f5;
                border: 1rpx dashed #dcdcdc;
                padding: 0; // 重置button默认样式
                margin: 0; // 重置button默认样式
                line-height: 1; // 重置button默认样式

                .avatar {
                    width: 100%;
                    height: 100%;
                }

                .avatar-placeholder {
                    width: 100%;
                    height: 100%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }

                .change-text {
                    position: absolute;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.5);
                    color: #ffffff;
                    font-size: 20rpx;
                    text-align: center;
                    padding: 8rpx 0;
                }
            }
        }

        .setting-content {
            padding: 20rpx;
        }
    }

    :deep(.wd-form) {
        margin-top: 20rpx;
        background: #ffffff;
        border-radius: 16rpx;
    }

    .button-wrap {
        margin-top: 40rpx;
        padding: 20rpx;
    }
}
</style>
