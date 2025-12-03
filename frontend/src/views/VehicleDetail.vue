<template>
  <div class="vehicle-detail-page">
    <header class="header">
      <div class="container">
        <router-link to="/" class="logo">租车系统</router-link>
        <nav class="nav">
          <router-link to="/vehicles">车辆列表</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/user">个人中心</router-link>
        </nav>
      </div>
    </header>

    <div class="container" v-loading="loading">
      <div v-if="vehicle" class="detail-content">
        <div class="image-gallery">
          <img :src="vehicle.images || '/api/placeholder-car.jpg'" :alt="vehicle.model" />
        </div>

        <div class="vehicle-info">
          <h1>{{ vehicle.brand }} {{ vehicle.model }}</h1>
          <div class="price">¥{{ vehicle.dailyPrice }}/天</div>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="车型">{{ vehicle.brand }} {{ vehicle.model }}</el-descriptions-item>
            <el-descriptions-item label="年份">{{ vehicle.year }}年</el-descriptions-item>
            <el-descriptions-item label="座位数">{{ vehicle.seats }}座</el-descriptions-item>
            <el-descriptions-item label="变速箱">{{ vehicle.transmission }}</el-descriptions-item>
            <el-descriptions-item label="燃料类型">{{ vehicle.fuelType }}</el-descriptions-item>
            <el-descriptions-item label="押金">¥{{ vehicle.deposit }}</el-descriptions-item>
          </el-descriptions>

          <el-button type="primary" size="large" @click="bookNow" style="margin-top: 2rem; width: 100%;">
            立即预订
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const vehicle = ref(null)

const loadVehicle = async () => {
  loading.value = true
  try {
    const response = await axios.get(`/api/vehicle/${route.params.id}`)
    if (response.data.success) {
      vehicle.value = response.data.data
    } else {
      ElMessage.error('车辆不存在')
      router.push('/vehicles')
    }
  } catch (error) {
    ElMessage.error('加载失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const bookNow = () => {
  router.push({
    path: '/booking',
    query: { vehicleId: vehicle.value.id }
  })
}

onMounted(() => {
  loadVehicle()
})
</script>

<style scoped>
.vehicle-detail-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.header {
  background: #1976D2;
  color: white;
  padding: 1rem 0;
}

.header .container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  font-size: 1.5rem;
  font-weight: bold;
  color: white;
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 2rem;
}

.nav a {
  color: white;
  text-decoration: none;
}

.container {
  max-width: 1200px;
  margin: 2rem auto;
  padding: 0 2rem;
}

.detail-content {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}

.image-gallery img {
  width: 100%;
  border-radius: 8px;
}

.vehicle-info h1 {
  margin-bottom: 1rem;
}

.price {
  font-size: 2rem;
  color: #1976D2;
  font-weight: bold;
  margin-bottom: 2rem;
}
</style>
