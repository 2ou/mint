<template>
  <div style="padding: 20px">
    <el-card>
      <template #header>创建任务</template>
      <el-form :model="form" label-width="100px">
        <el-form-item label="SPU"><el-input v-model="form.spu" /></el-form-item>
        <el-form-item label="Prompt"><el-input v-model="form.prompt" type="textarea" /></el-form-item>
        <el-form-item label="分辨率">
          <el-select v-model="form.resolution"><el-option label="1024" value="1024" /></el-select>
        </el-form-item>
        <el-form-item label="原图"><input type="file" @change="onFile($event, 'inputFile')" /></el-form-item>
        <el-form-item label="颜色图"><input type="file" @change="onFile($event, 'colorFile')" /></el-form-item>
        <el-button type="primary" @click="submit">开始生成</el-button>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>任务列表</template>
      <el-table :data="tasks" style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="spu" label="SPU" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="原图"><template #default="s"><img :src="s.row.inputImageUrl" width="80" /></template></el-table-column>
        <el-table-column label="颜色图"><template #default="s"><img :src="s.row.colorImageUrl" width="80" /></template></el-table-column>
        <el-table-column label="结果图"><template #default="s"><img :src="resultUrl(s.row)" width="80" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220">
          <template #default="s">
            <el-button size="small" @click="refresh(s.row.id)">刷新结果</el-button>
            <el-button size="small" type="success" @click="download(s.row.id)">下载结果图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createTask, downloadTask, getTaskList, refreshTask } from './api/task'
import { ElMessage } from 'element-plus'

const form = reactive({ spu: '', prompt: '', resolution: '1024', inputFile: null, colorFile: null })
const tasks = ref([])

const onFile = (e, key) => { form[key] = e.target.files[0] }
const resultUrl = (row) => row.resultOssUrl || row.resultTempUrl

const load = async () => {
  const res = await getTaskList(1, 20)
  tasks.value = res.data.data.content
}

const submit = async () => {
  const fd = new FormData()
  fd.append('spu', form.spu)
  fd.append('prompt', form.prompt)
  fd.append('resolution', form.resolution)
  fd.append('inputFile', form.inputFile)
  fd.append('colorFile', form.colorFile)
  const res = await createTask(fd)
  ElMessage.success(res.data.message)
  await load()
}

const refresh = async (id) => {
  await refreshTask(id)
  ElMessage.success('刷新完成')
  await load()
}

const download = async (id) => {
  const res = await downloadTask(id)
  const url = window.URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = `result_${id}.png`
  a.click()
  window.URL.revokeObjectURL(url)
}

onMounted(load)
</script>
