import { drawImage, expect, message, openChat, test } from './fixtures'

test('a new group gets a photo and a new name that members see live', async ({ createUser, signIn }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)

  await alicePage.getByTitle('New group').click()
  await alicePage.getByPlaceholder('Untitled group').fill('Reading Club')
  await alicePage.getByPlaceholder('Add people…').fill(bob.username)
  await alicePage.getByRole('button', { name: /Bob Hartley/ }).click()
  await alicePage.getByRole('button', { name: 'Create group' }).click()
  await expect(alicePage.locator('h1')).toContainText('Reading Club')
  await expect(bobPage.locator('nav a', { hasText: 'Reading Club' })).toBeVisible()

  await alicePage.getByTitle('Details').click()
  await alicePage.locator('aside input[type=file]').setInputFiles({
    name: 'club.jpg', mimeType: 'image/jpeg', buffer: await drawImage(alicePage, 800, 600, 'image/jpeg'),
  })
  await expect(bobPage.locator('nav a', { hasText: 'Reading Club' }).locator('img')).toBeVisible()

  await alicePage.getByLabel('Group name').fill('Harbour Readers')
  await alicePage.getByRole('button', { name: 'Rename' }).click()
  await expect(bobPage.locator('nav a', { hasText: 'Harbour Readers' })).toBeVisible()

  // only admins can change the photo
  await bobPage.locator('nav a', { hasText: 'Harbour Readers' }).click()
  await bobPage.getByTitle('Details').click()
  await expect(bobPage.locator('aside h2')).toHaveText('Harbour Readers')
  await expect(bobPage.locator('aside').getByTitle('Change photo')).toHaveCount(0)
})

test('a removed member keeps a read-only copy of the group', async ({ createUser, signIn, api, send }) => {
  const owner = await createUser('Olive Owner')
  const mira = await createUser('Mira Castell')
  const group = await api('POST', '/api/conversations/groups', { token: owner.token, data: { name: 'Old Circle', memberIds: [mira.id] } })
  await send(owner, group.id, 'before you left')

  const page = await signIn(mira)
  await openChat(page, group.id)
  await expect(message(page, 'before you left')).toBeVisible()

  await api('DELETE', `/api/conversations/groups/${group.id}/members/${mira.id}`, { token: owner.token })
  await expect(page.getByText('Read only.')).toBeVisible()
  await expect(page.locator('textarea')).toHaveCount(0)
  await expect(page.locator('nav a', { hasText: 'Old Circle' })).toContainText('No longer a member')

  // later messages never reach her
  await send(owner, group.id, 'after you left')
  await page.reload()
  await expect(message(page, 'before you left')).toBeVisible()
  await expect(message(page, 'after you left')).toHaveCount(0)
})
