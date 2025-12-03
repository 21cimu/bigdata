<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-card">
        <h2>{{ isRegister ? '注册账号' : '用户登录' }}</h2>
        
        <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
          
          <el-form-item v-if="isRegister" label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" />
          </el-form-item>
          
          <el-form-item v-if="isRegister" label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱" />
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="loading" style="width: 100%">
              {{ isRegister ? '注册' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
        
        <div class="switch-mode">
          <a @click="toggleMode">{{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const formRef = ref(null)
const isRegister = ref(false)
const loading = ref(false)

const form = ref({
  username: '',
  password: '',
  phone: '',
  email: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

const toggleMode = () => {
  isRegister.value = !isRegister.value
  form.value = {
    username: '',
    password: '',
    phone: '',
    email: ''
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const endpoint = isRegister.value ? '/api/auth?action=register' : '/api/auth?action=login'
        const response = await axios.post(endpoint, form.value)
        
        if (response.data.success) {
          ElMessage.success(isRegister.value ? '注册成功！' : '登录成功！')
          router.push('/')
        } else {
          ElMessage.error(response.data.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error(error.response?.data?.message || '网络错误')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-container {
  width: 100%;
  max-width: 450px;
  padding: 0 1rem;
}

.login-card {
  background: white;
  padding: 3rem 2rem;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.2);
}

.login-card h2 {
  text-align: center;
  margin-bottom: 2rem;
  color: #333;
}

.switch-mode {
  text-align: center;
  margin-top: 1rem;
}

.switch-mode a {
  color: #1976D2;
  cursor: pointer;
  text-decoration: none;
}

.switch-mode a:hover {
  text-decoration: underline;
}
</style>
