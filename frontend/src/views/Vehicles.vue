<template>
  <div class="vehicles-page">
    <header class="header">
      <div class="container">
        <router-link to="/" class="logo">租车系统</router-link>
        <nav class="nav">
          <router-link to="/">首页</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/user">个人中心</router-link>
        </nav>
      </div>
    </header>

    <div class="container">
      <div class="page-content">
        <aside class="sidebar">
          <div class="filter-section">
            <h3>车辆分类</h3>
            <el-radio-group v-model="filters.categoryId" @change="loadVehicles">
              <el-radio label="">全部</el-radio>
              <el-radio label="1">经济型</el-radio>
              <el-radio label="2">舒适型</el-radio>
              <el-radio label="3">SUV</el-radio>
              <el-radio label="4">商务型</el-radio>
              <el-radio label="5">豪华型</el-radio>
              <el-radio label="6">新能源</el-radio>
              <el-radio label="7">MPV</el-radio>
            </el-radio-group>
          </div>

          <div class="filter-section">
            <h3>座位数</h3>
            <el-checkbox-group v-model="filters.seats">
              <el-checkbox label="5">5座</el-checkbox>
              <el-checkbox label="7">7座</el-checkbox>
            </el-checkbox-group>
          </div>

          <div class="filter-section">
            <h3>变速箱</h3>
            <el-checkbox-group v-model="filters.transmission">
              <el-checkbox label="自动">自动</el-checkbox>
              <el-checkbox label="手动">手动</el-checkbox>
            </el-checkbox-group>
          </div>

          <div class="filter-section">
            <h3>能源类型</h3>
            <el-checkbox-group v-model="filters.fuelType">
              <el-checkbox label="汽油">汽油</el-checkbox>
              <el-checkbox label="电动">电动</el-checkbox>
              <el-checkbox label="混动">混动</el-checkbox>
            </el-checkbox-group>
          </div>

          <el-button type="primary" @click="loadVehicles" style="width: 100%; margin-top: 1rem;">
            应用筛选
          </el-button>
        </aside>

        <main class="main-content">
          <div class="search-bar">
            <el-input 
              v-model="searchKeyword" 
              placeholder="搜索车型、品牌" 
              @keyup.enter="loadVehicles"
            >
              <template #append>
                <el-button icon="Search" @click="loadVehicles" />
              </template>
            </el-input>

            <el-select v-model="sortBy" @change="loadVehicles" style="margin-left: 1rem; width: 150px;">
              <el-option label="默认排序" value="" />
              <el-option label="价格从低到高" value="price_asc" />
              <el-option label="价格从高到低" value="price_desc" />
            </el-select>
          </div>

          <div v-loading="loading" class="vehicle-list">
            <div v-if="vehicles.length === 0" class="empty-state">
              <p>暂无符合条件的车辆</p>
            </div>

            <div v-for="vehicle in vehicles" :key="vehicle.id" class="vehicle-item">
              <div class="vehicle-image">
                <img :src="vehicle.images || '/api/placeholder-car.jpg'" :alt="vehicle.model" />
              </div>
              <div class="vehicle-details">
                <h3>{{ vehicle.brand }} {{ vehicle.model }}</h3>
                <p class="vehicle-specs">
                  <span>{{ vehicle.year }}年</span>
                  <span>{{ vehicle.seats }}座</span>
                  <span>{{ vehicle.transmission }}</span>
                  <span>{{ vehicle.fuelType }}</span>
                </p>
                <p class="vehicle-store">门店: {{ vehicle.storeName || '全国连锁' }}</p>
                <div class="vehicle-features">
                  <el-tag size="small" type="success">免押金</el-tag>
                  <el-tag size="small">可送车</el-tag>
                </div>
              </div>
              <div class="vehicle-actions">
                <div class="price-info">
                  <div class="price">¥{{ vehicle.dailyPrice }}</div>
                  <div class="unit">/天</div>
                </div>
                <div class="deposit-info">押金: ¥{{ vehicle.deposit }}</div>
                <el-button type="primary" @click="viewVehicle(vehicle.id)">
                  立即租车
                </el-button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const vehicles = ref([])
const searchKeyword = ref('')
const sortBy = ref('')

const filters = ref({
  categoryId: '',
  seats: [],
  transmission: [],
  fuelType: []
})

const loadVehicles = async () => {
  loading.value = true
  try {
    const params = {}
    if (filters.value.categoryId) params.category = filters.value.categoryId
    if (filters.value.storeId) params.store = filters.value.storeId
    
    const response = await axios.get('/api/vehicle', { params })
    if (response.data.success) {
      let data = response.data.data
      
      // Client-side filtering
      if (filters.value.seats.length > 0) {
        data = data.filter(v => filters.value.seats.includes(String(v.seats)))
      }
      if (filters.value.transmission.length > 0) {
        data = data.filter(v => filters.value.transmission.includes(v.transmission))
      }
      if (filters.value.fuelType.length > 0) {
        data = data.filter(v => filters.value.fuelType.includes(v.fuelType))
      }
      if (searchKeyword.value) {
        const keyword = searchKeyword.value.toLowerCase()
        data = data.filter(v => 
          v.brand?.toLowerCase().includes(keyword) || 
          v.model?.toLowerCase().includes(keyword)
        )
      }
      
      // Sorting
      if (sortBy.value === 'price_asc') {
        data.sort((a, b) => (a.dailyPrice || 0) - (b.dailyPrice || 0))
      } else if (sortBy.value === 'price_desc') {
        data.sort((a, b) => (b.dailyPrice || 0) - (a.dailyPrice || 0))
      }
      
      vehicles.value = data
    }
  } catch (error) {
    ElMessage.error('加载车辆列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const viewVehicle = (id) => {
  router.push(`/vehicle/${id}`)
}

onMounted(() => {
  loadVehicles()
})
</script>

<style scoped>
.vehicles-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.header {
  background: #1976D2;
  color: white;
  padding: 1rem 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
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
  margin: 0 auto;
  padding: 2rem;
}

.page-content {
  display: grid;
  grid-template-columns: 250px 1fr;
  gap: 2rem;
}

.sidebar {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  height: fit-content;
}

.filter-section {
  margin-bottom: 2rem;
}

.filter-section h3 {
  margin-bottom: 1rem;
  font-size: 1rem;
}

.filter-section .el-radio-group,
.filter-section .el-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.main-content {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
}

.search-bar {
  display: flex;
  margin-bottom: 1.5rem;
}

.vehicle-list {
  min-height: 400px;
}

.empty-state {
  text-align: center;
  padding: 4rem 0;
  color: #999;
}

.vehicle-item {
  display: flex;
  gap: 1.5rem;
  padding: 1.5rem;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  margin-bottom: 1rem;
  transition: box-shadow 0.3s;
}

.vehicle-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.vehicle-image {
  width: 200px;
  height: 150px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  background: #f0f0f0;
}

.vehicle-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.vehicle-details {
  flex: 1;
}

.vehicle-details h3 {
  margin-bottom: 0.5rem;
  font-size: 1.25rem;
}

.vehicle-specs {
  color: #666;
  margin-bottom: 0.5rem;
}

.vehicle-specs span {
  margin-right: 1rem;
}

.vehicle-store {
  color: #999;
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
}

.vehicle-features {
  display: flex;
  gap: 0.5rem;
}

.vehicle-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  min-width: 150px;
}

.price-info {
  text-align: center;
}

.price {
  font-size: 2rem;
  font-weight: bold;
  color: #1976D2;
}

.unit {
  color: #666;
  font-size: 0.9rem;
}

.deposit-info {
  color: #999;
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
}
</style>
