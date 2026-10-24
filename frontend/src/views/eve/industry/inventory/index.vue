<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'

defineOptions({ name: 'EveIndustryInventory' })

const router = useRouter()
const activeTab = ref('purchase')

const purchaseRules = [
  ['材料来源', '吉他贸易中心 · 吉他海四空间站'],
  ['已有库存', '不扣除仓库里的材料，全部按市场采购估算'],
  ['采购价格', '从最低价卖单开始，按所需数量依次买入'],
  ['运输成本', '固定按 0 处理'],
]
</script>

<template>
  <main class="industry-inventory-page gi_page">
    <IndustryPageHeader eyebrow="工业生产" title="库存与材料" description="所有材料费用按吉他海四市场采购估算，已有库存暂不参与抵扣。">
      <template #actions>
        <a-button @click="router.push('/eve/industry/opportunities')">机会分析</a-button>
        <a-button type="primary" @click="router.push('/eve/market')">打开吉他市场</a-button>
      </template>
    </IndustryPageHeader>

    <a-alert class="industry-inventory-page__notice" type="warning" show-icon>
      当前工业计算不计库存，所有原材料一律按吉他海四市场购买；本页不会把未同步的军团资产或个人仓库数量计入成本。
    </a-alert>

    <IndustryPanel class="industry-inventory-page__panel" flush>
      <a-tabs v-model:active-key="activeTab" lazy-load>
        <a-tab-pane key="purchase" title="材料采购规则">
          <div class="industry-inventory-page__rules">
            <article v-for="item in purchaseRules" :key="item[0]"><span>{{ item[0] }}</span><strong>{{ item[1] }}</strong></article>
          </div>
          <a-alert class="industry-inventory-page__tip" type="info" show-icon>材料费用取决于当前卖单价格和可购数量；低价卖单不足时，会继续按更高价的卖单估算。</a-alert>
        </a-tab-pane>
        <a-tab-pane key="manual" title="人工库存">
          <a-empty description="暂不支持手动录入库存抵扣材料费用" />
        </a-tab-pane>
        <a-tab-pane key="esi" title="游戏内资产">
          <a-empty><p>军团资产暂不抵扣工业材料费用</p><a-space wrap><a-button type="primary" @click="router.push('/eve/assets')">查看军团资产</a-button><a-button @click="router.push('/eve/industry/opportunities')">返回机会分析</a-button></a-space></a-empty>
        </a-tab-pane>
      </a-tabs>
    </IndustryPanel>

    <IndustryPanel title="材料费用构成" description="材料采购总价与安装费相加，得到预计总投入。">
      <div class="industry-inventory-page__flow" aria-label="材料费用构成"><span>蓝图所需材料</span><i>→</i><span>吉他现有卖单</span><i>→</i><span>材料采购总价</span><i>→</i><span>预计总投入</span></div>
    </IndustryPanel>
  </main>
</template>

<style scoped lang="scss">
.industry-inventory-page { min-width: 0; overflow-x: clip; color: var(--color-text-1); }
.industry-inventory-page__notice, .industry-inventory-page__panel, .industry-inventory-page__flow { margin-top: .75rem; }
.industry-inventory-page__panel :deep(.arco-tabs-content) { padding-top: 0; }
.industry-inventory-page__rules { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem; }
.industry-inventory-page__rules article { display: flex; min-width: 0; flex-direction: column; gap: .25rem; padding: .8rem; border-radius: 10px; background: var(--color-fill-1); }
.industry-inventory-page__rules span { color: var(--color-text-3); font-size: .75rem; }
.industry-inventory-page__rules strong { overflow-wrap: anywhere; }
.industry-inventory-page__tip { margin-top: .75rem; }
.industry-inventory-page__flow { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; color: var(--color-text-2); }
.industry-inventory-page__flow span { padding: .55rem .7rem; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.industry-inventory-page__flow i { color: rgb(var(--arcoblue-6)); font-style: normal; }
@media (max-width: 600px) { .industry-inventory-page__rules { grid-template-columns: 1fr; } }
</style>
