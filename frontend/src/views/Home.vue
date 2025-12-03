<template>
  <div class="home">
    <header class="header">
      <div class="container">
        <div class="logo">租车系统</div>
        <nav class="nav">
          <router-link to="/vehicles">车辆列表</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/user">个人中心</router-link>
          <router-link to="/login" v-if="!isLoggedIn">登录</router-link>
          <span v-else>{{ username }}</span>
        </nav>
      </div>
    </header>

    <section class="hero">
      <div class="container">
        <h1>专业租车服务</h1>
        <p>安全、便捷、实惠的租车体验</p>
        
        <div class="search-box">
          <el-form :inline="true" :model="searchForm" class="search-form">
            <el-form-item label="取车时间">
              <el-date-picker
                v-model="searchForm.pickupTime"
                type="datetime"
                placeholder="选择日期时间"
              />
            </el-form-item>
            <el-form-item label="还车时间">
              <el-date-picker
                v-model="searchForm.returnTime"
                type="datetime"
                placeholder="选择日期时间"
              />
            </el-form-item>
            <el-form-item label="取车地点">
              <el-select v-model="searchForm.storeId" placeholder="选择门店">
                <el-option label="全部门店" value="" />
                <el-option label="机场店" value="1" />
                <el-option label="市区店" value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchVehicles">搜索车辆</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </section>

    <section class="features">
      <div class="container">
        <h2>为什么选择我们</h2>
        <div class="feature-grid">
          <div class="feature-item">
            <i class="icon">🚗</i>
            <h3>车型丰富</h3>
            <p>经济型、舒适型、SUV、豪华车应有尽有</p>
          </div>
          <div class="feature-item">
            <i class="icon">💰</i>
            <h3>价格实惠</h3>
            <p>透明定价，多种优惠活动，会员专享折扣</p>
          </div>
          <div class="feature-item">
            <i class="icon">⏰</i>
            <h3>服务便捷</h3>
            <p>24小时客服，送车上门，随时随地取还车</p>
          </div>
          <div class="feature-item">
            <i class="icon">🛡️</i>
            <h3>安全保障</h3>
            <p>全程保险，车辆定期维护，安全可靠</p>
          </div>
        </div>
      </div>
    </section>

    <section class="popular-vehicles">
      <div class="container">
        <h2>热门车型</h2>
        <div class="vehicle-grid">
          <div v-for="vehicle in popularVehicles" :key="vehicle.id" class="vehicle-card">
            <div class="vehicle-image">
              <img :src="vehicle.image || '/api/placeholder-car.jpg'" :alt="vehicle.model" />
            </div>
            <div class="vehicle-info">
              <h3>{{ vehicle.brand }} {{ vehicle.model }}</h3>
              <p class="vehicle-specs">{{ vehicle.seats }}座 | {{ vehicle.transmission }} | {{ vehicle.fuelType }}</p>
              <div class="vehicle-price">
                <span class="price">¥{{ vehicle.dailyPrice }}</span>
                <span class="unit">/天</span>
              </div>
              <el-button type="primary" @click="viewVehicle(vehicle.id)">立即预订</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <footer class="footer">
      <div class="container">
        <p>&copy; 2024 租车系统. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const isLoggedIn = ref(false)
const username = ref('')
const popularVehicles = ref([])

const searchForm = ref({
  pickupTime: new Date(),
  returnTime: new Date(Date.now() + 24 * 60 * 60 * 1000),
  storeId: ''
})

const searchVehicles = () => {
  router.push({
    path: '/vehicles',
    query: {
      pickupTime: searchForm.value.pickupTime.toISOString(),
      returnTime: searchForm.value.returnTime.toISOString(),
      storeId: searchForm.value.storeId
    }
  })
}

const viewVehicle = (id) => {
  router.push(`/vehicle/${id}`)
}

onMounted(async () => {
  try {
    // Load popular vehicles
    const response = await axios.get('/api/vehicle?status=AVAILABLE')
    if (response.data.success) {
      popularVehicles.value = response.data.data.slice(0, 6)
    }
  } catch (error) {
    console.error('Failed to load vehicles:', error)
  }
})
</script>

<style scoped>
.home {
  min-height: 100vh;
}

.header {
  background: #1976D2;
  color: white;
  padding: 1rem 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
}

.header .container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  font-size: 1.5rem;
  font-weight: bold;
}

.nav {
  display: flex;
  gap: 2rem;
  align-items: center;
}

.nav a {
  color: white;
  text-decoration: none;
  transition: opacity 0.3s;
}

.nav a:hover {
  opacity: 0.8;
}

.hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 4rem 0;
  text-align: center;
}

.hero h1 {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.hero p {
  font-size: 1.25rem;
  margin-bottom: 2rem;
}

.search-box {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 1rem;
}

.features {
  padding: 4rem 0;
  background: #f5f5f5;
}

.features h2 {
  text-align: center;
  font-size: 2rem;
  margin-bottom: 3rem;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 2rem;
}

.feature-item {
  text-align: center;
  padding: 2rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.feature-item .icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  display: block;
}

.feature-item h3 {
  margin-bottom: 0.5rem;
}

.popular-vehicles {
  padding: 4rem 0;
}

.popular-vehicles h2 {
  text-align: center;
  font-size: 2rem;
  margin-bottom: 3rem;
}

.vehicle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 2rem;
}

.vehicle-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: transform 0.3s;
}

.vehicle-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.vehicle-image {
  height: 200px;
  overflow: hidden;
  background: #f0f0f0;
}

.vehicle-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.vehicle-info {
  padding: 1.5rem;
}

.vehicle-info h3 {
  margin-bottom: 0.5rem;
}

.vehicle-specs {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.vehicle-price {
  margin-bottom: 1rem;
}

.price {
  font-size: 1.5rem;
  font-weight: bold;
  color: #1976D2;
}

.unit {
  color: #666;
  font-size: 0.9rem;
}

.footer {
  background: #333;
  color: white;
  padding: 2rem 0;
  text-align: center;
  margin-top: 4rem;
}
</style>
